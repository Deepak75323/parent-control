package com.parentalcontrol.billing.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.billing.domain.Payment;
import com.parentalcontrol.billing.domain.Subscription;
import com.parentalcontrol.billing.domain.SubscriptionPlan;
import com.parentalcontrol.billing.domain.SubscriptionStatus;
import com.parentalcontrol.billing.dto.CheckoutSessionResponse;
import com.parentalcontrol.billing.dto.PlanResponse;
import com.parentalcontrol.billing.dto.SubscriptionResponse;
import com.parentalcontrol.billing.repository.PaymentRepository;
import com.parentalcontrol.billing.repository.SubscriptionRepository;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.BillingProperties;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.repository.ParentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final BillingProperties billingProperties;
    private final StripeCheckoutService stripeCheckoutService;
    private final AuditService auditService;

    @Transactional
    public Subscription getOrCreateSubscription(UUID parentProfileId) {
        return subscriptionRepository.findFirstByParentProfileIdOrderByCreatedAtDesc(parentProfileId)
                .orElseGet(() -> startTrial(parentProfileId));
    }

    @Transactional
    public Subscription startTrial(UUID parentProfileId) {
        Instant trialEnd = Instant.now().plus(billingProperties.getTrialDays(), ChronoUnit.DAYS);
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .parentProfileId(parentProfileId)
                .planId(SubscriptionPlan.STANDARD.getPlanId())
                .status(SubscriptionStatus.TRIAL)
                .trialEndsAt(trialEnd)
                .currentPeriodEnd(trialEnd)
                .build());
        auditService.log("SUBSCRIPTION_TRIAL_STARTED", "subscription:" + subscription.getId(), null,
                Map.of("parentProfileId", parentProfileId.toString(), "trialEndsAt", trialEnd.toString()));
        return subscription;
    }

    @Transactional(readOnly = true)
    public boolean isSubscriptionActive(UUID parentProfileId) {
        if (!billingProperties.isEnforcementEnabled()) {
            return true;
        }
        Subscription sub = subscriptionRepository.findFirstByParentProfileIdOrderByCreatedAtDesc(parentProfileId)
                .orElse(null);
        if (sub == null) {
            return false;
        }
        return isActive(sub);
    }

    public boolean isActive(Subscription sub) {
        Instant now = Instant.now();
        return switch (sub.getStatus()) {
            case TRIAL -> sub.getTrialEndsAt() != null && sub.getTrialEndsAt().isAfter(now);
            case ACTIVE -> sub.getCurrentPeriodEnd() == null || sub.getCurrentPeriodEnd().isAfter(now);
            case PAST_DUE -> sub.getCurrentPeriodEnd() != null && sub.getCurrentPeriodEnd().isAfter(now);
            default -> false;
        };
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrentForParent() {
        ParentProfile parent = requireParent();
        Subscription sub = getOrCreateSubscription(parent.getId());
        return toResponse(sub);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return Arrays.stream(SubscriptionPlan.values())
                .map(p -> PlanResponse.builder()
                        .planId(p.getPlanId())
                        .maxDevices(p.getMaxDevices())
                        .liveStreamingAllowed(p.isLiveStreamingAllowed())
                        .build())
                .toList();
    }

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(String planId) {
        ParentProfile parent = requireParent();
        SubscriptionPlan plan = SubscriptionPlan.fromPlanId(planId);
        if (plan == SubscriptionPlan.FREE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cannot checkout free plan");
        }
        String url = stripeCheckoutService.createCheckoutSession(parent.getId(), plan);
        auditService.log("BILLING_CHECKOUT_STARTED", "plan:" + planId, null, Map.of("planId", planId));
        return CheckoutSessionResponse.builder().checkoutUrl(url).planId(plan.getPlanId()).build();
    }

    @Transactional
    public void activateFromProvider(
            UUID parentProfileId,
            String planId,
            String provider,
            String customerId,
            String subscriptionId,
            Instant periodEnd) {
        Subscription sub = subscriptionRepository.findFirstByParentProfileIdOrderByCreatedAtDesc(parentProfileId)
                .orElseGet(() -> Subscription.builder().parentProfileId(parentProfileId).build());
        sub.setPlanId(planId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setProvider(provider);
        sub.setProviderCustomerId(customerId);
        sub.setProviderSubscriptionId(subscriptionId);
        sub.setCurrentPeriodEnd(periodEnd);
        sub.setTrialEndsAt(null);
        subscriptionRepository.save(sub);
        auditService.log("SUBSCRIPTION_ACTIVATED", "subscription:" + sub.getId(), null,
                Map.of("planId", planId, "provider", provider));
    }

    @Transactional
    public void recordPayment(UUID subscriptionId, long amountCents, String currency, String provider,
                              String providerRef, String status) {
        if (paymentRepository.findByProviderRef(providerRef).isPresent()) {
            return;
        }
        paymentRepository.save(Payment.builder()
                .subscriptionId(subscriptionId)
                .amountCents(amountCents)
                .currency(currency)
                .provider(provider)
                .providerRef(providerRef)
                .status(status)
                .build());
    }

    @Transactional
    public void markCancelled(String providerSubscriptionId) {
        subscriptionRepository.findByProviderSubscriptionId(providerSubscriptionId).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);
            auditService.log("SUBSCRIPTION_CANCELLED", "subscription:" + sub.getId(), null, Map.of());
        });
    }

    @Transactional
    public void markPastDue(String providerSubscriptionId, Instant periodEnd) {
        subscriptionRepository.findByProviderSubscriptionId(providerSubscriptionId).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.PAST_DUE);
            sub.setCurrentPeriodEnd(periodEnd);
            subscriptionRepository.save(sub);
        });
    }

    private ParentProfile requireParent() {
        UUID userId = SecurityUtils.requireUserId();
        return parentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Parent profile not found"));
    }

    private SubscriptionResponse toResponse(Subscription sub) {
        SubscriptionPlan plan = SubscriptionPlan.fromPlanId(sub.getPlanId());
        return SubscriptionResponse.builder()
                .id(sub.getId())
                .planId(sub.getPlanId())
                .status(sub.getStatus().name())
                .active(isActive(sub))
                .trialEndsAt(sub.getTrialEndsAt())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .maxDevices(plan.getMaxDevices())
                .liveStreamingAllowed(plan.isLiveStreamingAllowed() || sub.getStatus() == SubscriptionStatus.TRIAL)
                .build();
    }
}
