package com.parentalcontrol.device.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.AuthenticatedUser;
import com.parentalcontrol.config.DeviceCommandProperties;
import com.parentalcontrol.config.SecurityProperties;
import com.parentalcontrol.device.dao.DeviceCommandDao;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceCommand;
import com.parentalcontrol.device.domain.DeviceCommandType;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.dto.DeviceCommandRequest;
import com.parentalcontrol.observability.CommandObservabilityService;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.service.ParentPinService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DeviceCommandServiceRevokePasswordTest {

  @Mock private DeviceCommandDao deviceCommandRepository;
  @Mock private DeviceDao deviceRepository;
  @Mock private ParentProfileDao parentProfileRepository;
  @Mock private UserDao userRepository;
  @Mock private AuditService auditService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private DeviceCommandProperties deviceCommandProperties;
  @Mock private SecurityProperties securityProperties;
  @Mock private ParentPinService parentPinService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ObjectMapper objectMapper;
  @Mock private CommandObservabilityService commandObservabilityService;
  @Mock private FeatureGateService featureGateService;

  @InjectMocks private DeviceCommandService deviceCommandService;

  private final UUID userId = UUID.randomUUID();
  private final UUID parentProfileId = UUID.randomUUID();
  private final UUID deviceId = UUID.randomUUID();

  @BeforeEach
  void setUpSecurity() {
    AuthenticatedUser parent =
        AuthenticatedUser.builder()
            .id(userId)
            .email("parent@example.com")
            .passwordHash("hash")
            .role(Role.ROLE_PARENT)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(parent, null, parent.getAuthorities()));
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void revokeWithoutPasswordIsRejected() {
    stubOwnedOnlineDevice();
    DeviceCommandRequest request = new DeviceCommandRequest();
    request.setCommandType(DeviceCommandType.REVOKE_MONITORING);

    assertThatThrownBy(() -> deviceCommandService.sendCommand(deviceId, request))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    verify(deviceCommandRepository, never()).save(any());
  }

  @Test
  void revokeWithWrongPasswordIsRejected() {
    stubOwnedOnlineDevice();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
    when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

    DeviceCommandRequest request = new DeviceCommandRequest();
    request.setCommandType(DeviceCommandType.REVOKE_MONITORING);
    request.setPassword("wrong");

    assertThatThrownBy(() -> deviceCommandService.sendCommand(deviceId, request))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    verify(deviceCommandRepository, never()).save(any());
  }

  @Test
  void revokeWithCorrectPasswordIsAccepted() throws Exception {
    stubOwnedOnlineDevice();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
    when(passwordEncoder.matches("correct-pass", "hash")).thenReturn(true);
    when(securityProperties.isRemoteControlPinRequired()).thenReturn(false);
    when(objectMapper.writeValueAsString(any())).thenReturn(null);
    when(deviceCommandRepository.save(any()))
        .thenAnswer(
            inv -> {
              DeviceCommand cmd = inv.getArgument(0);
              cmd.setId(UUID.randomUUID());
              return cmd;
            });

    DeviceCommandRequest request = new DeviceCommandRequest();
    request.setCommandType(DeviceCommandType.REVOKE_MONITORING);
    request.setPassword("correct-pass");

    deviceCommandService.sendCommand(deviceId, request);
    verify(deviceCommandRepository).save(any());
  }

  private void stubOwnedOnlineDevice() {
    ParentProfile profile = ParentProfile.builder().user(user()).build();
    profile.setId(parentProfileId);
    when(parentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

    Device device =
        Device.builder()
            .parentProfileId(parentProfileId)
            .deviceName("Child")
            .status(DeviceStatus.ONLINE)
            .build();
    device.setId(deviceId);
    when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
  }

  private User user() {
    User user = new User();
    user.setId(userId);
    user.setEmail("parent@example.com");
    user.setPasswordHash("hash");
    user.setRole(Role.ROLE_PARENT);
    return user;
  }
}
