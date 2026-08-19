package com.parentalcontrol.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.service.FcmWakeService;
import com.parentalcontrol.monitoring.dao.ScreenCaptureAuditLogDao;
import com.parentalcontrol.monitoring.dao.ScreenSessionDao;
import com.parentalcontrol.monitoring.domain.ScreenSession;
import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import com.parentalcontrol.observability.StreamObservabilityService;
import com.parentalcontrol.policy.dao.PolicyAssignmentDao;
import com.parentalcontrol.policy.dao.PolicyDao;
import com.parentalcontrol.policy.dao.StreamingPolicyRuleDao;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.service.ParentPinService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamingServiceSwitchCameraTest {

  @Mock private ScreenSessionDao screenSessionRepository;
  @Mock private DeviceAccessService deviceAccessService;
  @Mock private RealtimeEventService realtimeEventService;
  @Mock private ParentProfileDao parentProfileRepository;
  @Mock private PolicyAssignmentDao policyAssignmentRepository;
  @Mock private PolicyDao policyRepository;
  @Mock private StreamingPolicyRuleDao streamingPolicyRuleRepository;
  @Mock private ParentPinService parentPinService;
  @Mock private ScreenCaptureAuditLogDao screenCaptureAuditLogDao;
  @Mock private AuditService auditService;
  @Mock private FeatureGateService featureGateService;
  @Mock private SignalingCleanupScheduler signalingCleanupScheduler;
  @Mock private FcmWakeService fcmWakeService;
  @Mock private StreamObservabilityService streamObservabilityService;

  @InjectMocks private StreamingService streamingService;

  @Test
  void switchCamera_publishesStompAndFcmWake_forActiveCameraSession() {
    UUID sessionId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();
    ScreenSession session = activeCameraSession(sessionId, deviceId);
    Device device = new Device();
    device.setId(deviceId);

    when(screenSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(deviceAccessService.requireOwnedDevice(deviceId)).thenReturn(device);

    streamingService.switchCamera(sessionId);

    verify(realtimeEventService).publishDeviceStreamControl(deviceId, sessionId, "SWITCH_CAMERA");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
    verify(fcmWakeService).wakeDevice(eq(device), eq("SWITCH_CAMERA"), payload.capture());
    assertThat(payload.getValue())
        .containsEntry("sessionId", sessionId.toString())
        .containsEntry("command", "SWITCH_CAMERA");
  }

  @Test
  void switchCamera_doesNotWake_whenSessionAlreadyEnded() {
    UUID sessionId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();
    ScreenSession session = activeCameraSession(sessionId, deviceId);
    session.setStatus(ScreenSessionStatus.ENDED);
    Device device = new Device();
    device.setId(deviceId);

    when(screenSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(deviceAccessService.requireOwnedDevice(deviceId)).thenReturn(device);

    assertThatThrownBy(() -> streamingService.switchCamera(sessionId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    verify(fcmWakeService, never())
        .wakeDevice(eq(device), eq("SWITCH_CAMERA"), org.mockito.ArgumentMatchers.anyMap());
  }

  private static ScreenSession activeCameraSession(UUID sessionId, UUID deviceId) {
    ScreenSession session = new ScreenSession();
    session.setId(sessionId);
    session.setDeviceId(deviceId);
    session.setStreamType("CAMERA");
    session.setStatus(ScreenSessionStatus.ACTIVE);
    session.setStartedAt(Instant.now());
    return session;
  }
}
