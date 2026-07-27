package com.parentalcontrol.billing.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.parentalcontrol.billing.domain.Subscription;
import com.parentalcontrol.billing.domain.SubscriptionPlan;
import com.parentalcontrol.billing.domain.SubscriptionStatus;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.BillingProperties;
import com.parentalcontrol.device.dao.DeviceDao;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureGateServiceTest {

  @Mock private SubscriptionService subscriptionService;
  @Mock private DeviceDao deviceRepository;
  @Mock private BillingProperties billingProperties;

  private FeatureGateService featureGateService;
  private final UUID parentProfileId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    featureGateService =
        new FeatureGateService(subscriptionService, deviceRepository, billingProperties);
  }

  @Test
  void requireDeviceSlot_allowsPairingWhenOnlyRevokedDevicesExist() {
    stubActiveStandardSubscription();
    when(deviceRepository.countNonRevokedByParentProfileId(parentProfileId)).thenReturn(0L);

    assertThatCode(() -> featureGateService.requireDeviceSlot(parentProfileId))
        .doesNotThrowAnyException();
  }

  @Test
  void requireDeviceSlot_blocksWhenNonRevokedCountReachesPlanMax() {
    stubActiveStandardSubscription();
    when(deviceRepository.countNonRevokedByParentProfileId(parentProfileId))
        .thenReturn((long) SubscriptionPlan.STANDARD.getMaxDevices());

    assertThatThrownBy(() -> featureGateService.requireDeviceSlot(parentProfileId))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);
  }

  private void stubActiveStandardSubscription() {
    when(billingProperties.isEnforcementEnabled()).thenReturn(true);
    when(subscriptionService.isSubscriptionActive(parentProfileId)).thenReturn(true);
    Subscription sub =
        Subscription.builder()
            .parentProfileId(parentProfileId)
            .planId(SubscriptionPlan.STANDARD.getPlanId())
            .status(SubscriptionStatus.ACTIVE)
            .build();
    when(subscriptionService.findSubscription(parentProfileId)).thenReturn(Optional.of(sub));
  }
}
