package com.parentalcontrol.billing.service;

import com.parentalcontrol.billing.domain.Subscription;
import com.parentalcontrol.billing.domain.SubscriptionPlan;
import com.parentalcontrol.billing.domain.SubscriptionStatus;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.BillingProperties;
import com.parentalcontrol.device.dao.DeviceDao;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeatureGateService {

  private final SubscriptionService subscriptionService;
  private final DeviceDao deviceRepository;
  private final BillingProperties billingProperties;

  public void requireActiveSubscription(UUID parentProfileId) {
    if (!billingProperties.isEnforcementEnabled()) {
      return;
    }
    if (!subscriptionService.isSubscriptionActive(parentProfileId)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN,
          MessageConstants
              .ACTIVE_SUBSCRIPTION_REQUIRED_VERIFY_YOUR_EMAIL_AND_START_A_TRIAL_OR_UPGRADE_YOUR);
    }
  }

  public void requireLiveStreaming(UUID parentProfileId) {
    requireActiveSubscription(parentProfileId);
    Subscription sub = requireExistingSubscription(parentProfileId);
    SubscriptionPlan plan = SubscriptionPlan.fromPlanId(sub.getPlanId());
    if (!plan.isLiveStreamingAllowed() && sub.getStatus() != SubscriptionStatus.TRIAL) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.LIVE_STREAMING_REQUIRES_STANDARD_OR_FAMILY_PLAN);
    }
  }

  public void requireDeviceSlot(UUID parentProfileId) {
    if (!billingProperties.isEnforcementEnabled()) {
      return;
    }
    if (!subscriptionService.isSubscriptionActive(parentProfileId)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.ACTIVE_SUBSCRIPTION_REQUIRED_TO_ADD_DEVICES);
    }
    Subscription sub = requireExistingSubscription(parentProfileId);
    SubscriptionPlan plan = SubscriptionPlan.fromPlanId(sub.getPlanId());
    // Revoked devices do not consume a seat — revoke frees the slot for another child phone.
    long deviceCount = deviceRepository.countNonRevokedByParentProfileId(parentProfileId);
    if (deviceCount >= plan.getMaxDevices()) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN,
          MessageConstants.DEVICE_LIMIT_REACHED_FOR_PLAN
              + plan.getPlanId()
              + " (max "
              + plan.getMaxDevices()
              + ")");
    }
  }

  public SubscriptionPlan effectivePlan(UUID parentProfileId) {
    if (!subscriptionService.isSubscriptionActive(parentProfileId)) {
      return SubscriptionPlan.FREE;
    }
    Subscription sub = subscriptionService.findSubscription(parentProfileId).orElse(null);
    if (sub == null) {
      return SubscriptionPlan.FREE;
    }
    if (sub.getStatus() == SubscriptionStatus.TRIAL) {
      return SubscriptionPlan.STANDARD;
    }
    return SubscriptionPlan.fromPlanId(sub.getPlanId());
  }

  private Subscription requireExistingSubscription(UUID parentProfileId) {
    return subscriptionService
        .findSubscription(parentProfileId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.FORBIDDEN, MessageConstants.ACTIVE_SUBSCRIPTION_REQUIRED));
  }
}
