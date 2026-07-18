package com.parentalcontrol.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.service.DeviceCommandService;
import com.parentalcontrol.monitoring.service.DeviceAccessService;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import com.parentalcontrol.policy.dao.AppBlockRuleDao;
import com.parentalcontrol.policy.dao.DeviceScheduleRuleDao;
import com.parentalcontrol.policy.dao.GeofenceRuleDao;
import com.parentalcontrol.policy.dao.PolicyAssignmentDao;
import com.parentalcontrol.policy.dao.PolicyDao;
import com.parentalcontrol.policy.dao.PolicySyncStateDao;
import com.parentalcontrol.policy.dao.ScreenTimeRuleDao;
import com.parentalcontrol.policy.dao.StreamingPolicyRuleDao;
import com.parentalcontrol.policy.domain.Policy;
import com.parentalcontrol.policy.domain.PolicyAssignment;
import com.parentalcontrol.policy.domain.PolicyType;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PolicyServiceDeleteTest {

  @Mock private PolicyDao policyRepository;
  @Mock private AppBlockRuleDao appBlockRuleRepository;
  @Mock private ScreenTimeRuleDao screenTimeRuleRepository;
  @Mock private DeviceScheduleRuleDao deviceScheduleRuleRepository;
  @Mock private GeofenceRuleDao geofenceRuleRepository;
  @Mock private StreamingPolicyRuleDao streamingPolicyRuleRepository;
  @Mock private PolicyAssignmentDao policyAssignmentRepository;
  @Mock private PolicySyncStateDao policySyncStateRepository;
  @Mock private ParentProfileDao parentProfileRepository;
  @Mock private DeviceAccessService deviceAccessService;
  @Mock private FeatureGateService featureGateService;
  @Mock private RealtimeEventService realtimeEventService;
  @Mock private AuditService auditService;
  @Mock private PolicyVersionHasher policyVersionHasher;
  @Mock private TimeExtensionService timeExtensionService;
  @Mock private DeviceCommandService deviceCommandService;

  @InjectMocks private PolicyService policyService;

  private MockedStatic<SecurityUtils> securityUtils;

  @BeforeEach
  void setUp() {
    securityUtils = org.mockito.Mockito.mockStatic(SecurityUtils.class);
  }

  @AfterEach
  void tearDown() {
    securityUtils.close();
  }

  @Test
  void deletePolicy_softDeletesWithoutHardDelete() {
    UUID userId = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();

    ParentProfile parent = ParentProfile.builder().build();
    parent.setId(parentId);

    Policy policy =
        Policy.builder()
            .parentProfileId(parentId)
            .name("Block All Apps")
            .policyType(PolicyType.APP_BLOCK)
            .enabled(true)
            .version(1)
            .build();
    policy.setId(policyId);

    PolicyAssignment assignment =
        PolicyAssignment.builder().policyId(policyId).deviceId(deviceId).active(true).build();

    securityUtils.when(SecurityUtils::requireUserId).thenReturn(userId);
    when(parentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(parent));
    when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));
    when(policyAssignmentRepository.findByPolicyId(policyId)).thenReturn(List.of(assignment));
    when(policyAssignmentRepository.findActivePolicyIdsByDeviceId(deviceId)).thenReturn(List.of());
    when(timeExtensionService.activeGrantsForDevice(deviceId)).thenReturn(List.of());
    when(policyVersionHasher.hash(any())).thenReturn("v1");
    when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

    policyService.deletePolicy(policyId);

    ArgumentCaptor<Policy> saved = ArgumentCaptor.forClass(Policy.class);
    verify(policyRepository).save(saved.capture());
    verify(policyRepository, org.mockito.Mockito.never()).delete(any());
    assertThat(saved.getValue().getEnabled()).isFalse();
    assertThat(saved.getValue().getDeletedAt()).isNotNull();
    assertThat(assignment.getActive()).isFalse();
  }
}
