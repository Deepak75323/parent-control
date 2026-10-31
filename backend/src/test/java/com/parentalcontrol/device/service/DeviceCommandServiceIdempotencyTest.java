package com.parentalcontrol.device.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.security.AuthenticatedUser;
import com.parentalcontrol.config.DeviceCommandProperties;
import com.parentalcontrol.device.dao.DeviceCommandDao;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.DeviceCommand;
import com.parentalcontrol.device.domain.DeviceCommandStatus;
import com.parentalcontrol.device.domain.DeviceCommandType;
import com.parentalcontrol.user.dao.ParentProfileDao;
import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
class DeviceCommandServiceIdempotencyTest {

  @Mock private DeviceCommandDao deviceCommandRepository;
  @Mock private DeviceDao deviceRepository;
  @Mock private ParentProfileDao parentProfileRepository;
  @Mock private com.parentalcontrol.user.dao.UserDao userRepository;
  @Mock private AuditService auditService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private DeviceCommandProperties deviceCommandProperties;
  @Mock private ObjectMapper objectMapper;
  @Mock private com.parentalcontrol.config.SecurityProperties securityProperties;
  @Mock private com.parentalcontrol.user.service.ParentPinService parentPinService;
  @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

  @Mock
  private com.parentalcontrol.observability.CommandObservabilityService commandObservabilityService;

  @Mock private com.parentalcontrol.billing.service.FeatureGateService featureGateService;

  @InjectMocks private DeviceCommandService deviceCommandService;

  private final UUID deviceId = UUID.randomUUID();
  private final UUID commandId = UUID.randomUUID();

  @BeforeEach
  void setUpSecurity() {
    AuthenticatedUser deviceUser =
        AuthenticatedUser.builder()
            .id(UUID.randomUUID())
            .email("device@test")
            .passwordHash("")
            .role(Role.ROLE_CHILD_DEVICE)
            .deviceId(deviceId)
            .build();
    var auth =
        new UsernamePasswordAuthenticationToken(deviceUser, null, deviceUser.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void recordCommandExecution_setsFingerprintWhenDelivered() {
    DeviceCommand command = deliveredCommand();
    when(deviceCommandRepository.findById(commandId)).thenReturn(Optional.of(command));
    when(deviceCommandRepository.recordExecutionFingerprint(
            eq(commandId), eq(deviceId), any(Instant.class), eq(true), eq(null)))
        .thenReturn(1);

    deviceCommandService.recordCommandExecution(commandId, true, null);

    verify(deviceCommandRepository)
        .recordExecutionFingerprint(
            eq(commandId), eq(deviceId), any(Instant.class), eq(true), eq(null));
  }

  @Test
  void recordCommandExecution_isNoOpWhenFingerprintAlreadyPresent() {
    DeviceCommand command = deliveredCommand();
    command.setExecutedAt(Instant.now());
    command.setExecutionSuccess(true);
    when(deviceCommandRepository.findById(commandId)).thenReturn(Optional.of(command));

    deviceCommandService.recordCommandExecution(commandId, true, null);

    verify(deviceCommandRepository, never())
        .recordExecutionFingerprint(any(), any(), any(), eq(true), any());
  }

  @Test
  void acknowledgeCommand_isIdempotentWhenAlreadyCompleted() {
    DeviceCommand command = deliveredCommand();
    command.setStatus(DeviceCommandStatus.COMPLETED);
    when(deviceCommandRepository.findById(commandId)).thenReturn(Optional.of(command));
    when(deviceCommandRepository.completeDeliveredCommand(
            eq(commandId), eq(deviceId), any(Instant.class)))
        .thenReturn(0);

    deviceCommandService.acknowledgeCommand(commandId, true, null);

    verify(deviceRepository, never()).findById(any());
  }

  private DeviceCommand deliveredCommand() {
    return DeviceCommand.builder()
        .id(commandId)
        .deviceId(deviceId)
        .commandType(DeviceCommandType.REVOKE_MONITORING)
        .status(DeviceCommandStatus.DELIVERED)
        .deliveryAttemptCount(1)
        .build();
  }
}
