package com.parentalcontrol.billing.service;

import com.parentalcontrol.billing.domain.Subscription;
import com.parentalcontrol.billing.domain.SubscriptionPlan;
import com.parentalcontrol.billing.domain.SubscriptionStatus;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.BillingProperties;
import com.parentalcontrol.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeatureGateService {

    private final SubscriptionService subscriptionService;
    private final DeviceRepository deviceRepository;
    private final BillingProperties billingProperties;

    public void requireActiveSubscription(UUID parentProfileId) {
        if (!billingProperties.isEnforcementEnabled()) {
            return;
        }
        if (!subscriptionService.isSubscriptionActive(parentProfileId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Active subscription required. Start a trial or upgrade your plan.");
        }
    }

    public void requireLiveStreaming(UUID parentProfileId) {
        requireActiveSubscription(parentProfileId);
        Subscription sub = subscriptionService.getOrCreateSubscription(parentProfileId);
        SubscriptionPlan plan = SubscriptionPlan.fromPlanId(sub.getPlanId());
        if (!plan.isLiveStreamingAllowed() && sub.getStatus() != SubscriptionStatus.TRIAL) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Live streaming requires Standard or Family plan");
        }
    }

    public void requireDeviceSlot(UUID parentProfileId) {
        if (!billingProperties.isEnforcementEnabled()) {
            return;
        }
        Subscription sub = subscriptionService.getOrCreateSubscription(parentProfileId);
        if (!subscriptionService.isSubscriptionActive(parentProfileId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Active subscription required to add devices");
        }
        SubscriptionPlan plan = SubscriptionPlan.fromPlanId(sub.getPlanId());
        long deviceCount = deviceRepository.countByParentProfileId(parentProfileId);
        if (deviceCount >= plan.getMaxDevices()) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Device limit reached for plan " + plan.getPlanId() + " (max " + plan.getMaxDevices() + ")");
        }
    }

    public SubscriptionPlan effectivePlan(UUID parentProfileId) {
        Subscription sub = subscriptionService.getOrCreateSubscription(parentProfileId);
        if (!subscriptionService.isSubscriptionActive(parentProfileId)) {
            return SubscriptionPlan.FREE;
        }
        if (sub.getStatus() == SubscriptionStatus.TRIAL) {
            return SubscriptionPlan.STANDARD;
        }
        return SubscriptionPlan.fromPlanId(sub.getPlanId());
    }
}
