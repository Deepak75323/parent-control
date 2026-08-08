package com.parentalcontrol.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.common.security.SecretEncryptionService;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.dao.DevicePermissionStatusDao;
import com.parentalcontrol.device.dao.DeviceProvisioningEventDao;
import com.parentalcontrol.device.dao.PairingCodeDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.dto.DeviceWakeResponse;
import com.parentalcontrol.device.security.DeviceHmacService;
import com.parentalcontrol.monitoring.kafka.MonitoringKafkaPublisher;
import com.parentalcontrol.monitoring.service.AlertService;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import com.parentalcontrol.observability.DeviceHealthObservabilityService;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.service.DefaultChildLoginPasswordService;
import com.parentalcontrol.user.service.FamilyHandleService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Covers the parent-initiated FCM wake path (POST /v1/devices/{id}/wake).
 *
 * <p>Behavior under test:
 *
 * <ul>
 *   <li>Ownership is enforced — a parent cannot wake another parent's device.
 *   <li>FCM dispatch is called with {@code HEARTBEAT_NUDGE} so the child posts a fresh heartbeat.
 *   <li>Response reflects the current status/{@code lastSeenAt} so the parent has a baseline to
 *       poll against.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceWakeTest {

  @Mock private DeviceDao deviceRepository;
  @Mock private PairingCodeDao pairingCodeRepository;
  @Mock private DevicePermissionStatusDao permissionStatusRepository;
  @Mock private ParentProfileDao parentProfileRepository;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtProperties jwtProperties;
  @Mock private RealtimeEventService realtimeEventService;
  @Mock private AlertService alertService;
  @Mock private DeviceProvisioningEventDao deviceProvisioningEventRepository;
  @Mock private DeviceHmacService deviceHmacService;
  @Mock private FeatureGateService featureGateService;
  @Mock private SecretEncryptionService secretEncryptionService;
  @Mock private MonitoringKafkaPublisher monitoringKafkaPublisher;
  @Mock private DeviceTrustService deviceTrustService;
  @Mock private DeviceHealthObservabilityService deviceHealthObservabilityService;
  @Mock private DeviceCertificationService deviceCertificationService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private DefaultChildLoginPasswordService defaultChildLoginPasswordService;
  @Mock private FamilyHandleService familyHandleService;
  @Mock private FcmWakeService fcmWakeService;

  @InjectMocks private DeviceService deviceService;

  private MockedStatic<SecurityUtils> securityUtils;

  @BeforeEach
  void setUp() {
    securityUtils = Mockito.mockStatic(SecurityUtils.class);
  }

  @AfterEach
  void tearDown() {
    securityUtils.close();
  }

  @Test
  void wakeDevice_dispatchesHeartbeatNudge_forOwnedDevice() {
    UUID userId = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();
    securityUtils.when(SecurityUtils::requireUserId).thenReturn(userId);

    ParentProfile parent = new ParentProfile();
    parent.setId(parentId);
    when(parentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(parent));

    Instant lastSeen = Instant.now().minusSeconds(600);
    Device device = new Device();
    device.setId(deviceId);
    device.setParentProfileId(parentId);
    device.setStatus(DeviceStatus.OFFLINE);
    device.setLastSeenAt(lastSeen);
    device.setFcmToken("fake-fcm-token");
    when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

    DeviceWakeResponse response = deviceService.wakeDevice(deviceId);

    assertThat(response.getDeviceId()).isEqualTo(deviceId);
    assertThat(response.getStatus()).isEqualTo(DeviceStatus.OFFLINE);
    assertThat(response.getLastSeenAt()).isEqualTo(lastSeen);
    assertThat(response.isFcmDispatched()).isTrue();
    verify(fcmWakeService, times(1)).wakeDevice(eq(device), eq("HEARTBEAT_NUDGE"), anyMap());
  }

  @Test
  void wakeDevice_reportsFcmNotDispatched_whenDeviceHasNoFcmToken() {
    UUID userId = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();
    securityUtils.when(SecurityUtils::requireUserId).thenReturn(userId);

    ParentProfile parent = new ParentProfile();
    parent.setId(parentId);
    when(parentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(parent));

    Device device = new Device();
    device.setId(deviceId);
    device.setParentProfileId(parentId);
    device.setStatus(DeviceStatus.ONLINE);
    device.setLastSeenAt(Instant.now());
    device.setFcmToken(null);
    when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

    DeviceWakeResponse response = deviceService.wakeDevice(deviceId);

    // FCM service is still called (it internally logs the missing token). The response signals
    // dispatch=false so the parent can decide whether to keep polling or bail out early.
    verify(fcmWakeService, times(1)).wakeDevice(eq(device), eq("HEARTBEAT_NUDGE"), anyMap());
    assertThat(response.isFcmDispatched()).isFalse();
  }

  @Test
  void wakeDevice_rejects_whenDeviceBelongsToAnotherParent() {
    UUID userId = UUID.randomUUID();
    UUID myParentId = UUID.randomUUID();
    UUID otherParentId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();
    securityUtils.when(SecurityUtils::requireUserId).thenReturn(userId);

    ParentProfile parent = new ParentProfile();
    parent.setId(myParentId);
    when(parentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(parent));

    Device device = new Device();
    device.setId(deviceId);
    device.setParentProfileId(otherParentId);
    when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

    assertThatThrownBy(() -> deviceService.wakeDevice(deviceId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);

    verify(fcmWakeService, never()).wakeDevice(any(), any(), anyMap());
  }
}
