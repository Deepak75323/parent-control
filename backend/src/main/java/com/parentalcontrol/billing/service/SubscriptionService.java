package com.parentalcontrol.billing.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.billing.dao.PaymentDao;
import com.parentalcontrol.billing.dao.SubscriptionDao;
import com.parentalcontrol.billing.domain.Payment;
import com.parentalcontrol.billing.domain.Subscription;
import com.parentalcontrol.billing.domain.SubscriptionPlan;
import com.parentalcontrol.billing.domain.SubscriptionStatus;
import com.parentalcontrol.billing.dto.CheckoutSessionResponse;
import com.parentalcontrol.billing.dto.PlanResponse;
import com.parentalcontrol.billing.dto.SubscriptionResponse;
import com.parentalcontrol.common.constant.AuditActionConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.BillingProperties;
import com.parentalcontrol.config.EmailVerificationProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

  private final SubscriptionDao subscriptionRepository;
  private final PaymentDao paymentRepository;
  private final ParentProfileDao parentProfileRepository;
  private final UserDao userRepository;
  private final BillingProperties billingProperties;
  private final EmailVerificationProperties emailVerificationProperties;
  private final StripeCheckoutService stripeCheckoutService;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public java.util.Optional<Subscription> findSubscription(UUID parentProfileId) {
    return subscriptionRepository.findFirstByParentProfileIdOrderByCreatedAtDesc(parentProfileId);
  }

  @Transactional
  public Subscription getOrCreateSubscription(UUID parentProfileId) {
    return subscriptionRepository
        .findFirstByParentProfileIdOrderByCreatedAtDesc(parentProfileId)
        .orElseGet(() -> startTrialIfEligible(parentProfileId));
  }

  @Transactional
  public Subscription startTrialIfEligible(UUID parentProfileId) {
    if (!isTrialEligible(parentProfileId)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.VERIFY_YOUR_EMAIL_ADDRESS_TO_START_YOUR_FREE_TRIAL);
    }
    return startTrial(parentProfileId);
  }

  @Transactional
  public Subscription startTrial(UUID parentProfileId) {
    Instant trialEnd = Instant.now().plus(billingProperties.getTrialDays(), ChronoUnit.DAYS);
    Subscription subscription =
        subscriptionRepository.save(
            Subscription.builder()
                .parentProfileId(parentProfileId)
                .planId(SubscriptionPlan.STANDARD.getPlanId())
                .status(SubscriptionStatus.TRIAL)
                .trialEndsAt(trialEnd)
                .currentPeriodEnd(trialEnd)
                .build());
    auditService.log(
        AuditActionConstants.SUBSCRIPTION_TRIAL_STARTED,
        "subscription:" + subscription.getId(),
        null,
        Map.of("parentProfileId", parentProfileId.toString(), "trialEndsAt", trialEnd.toString()));
    return subscription;
  }

  @Transactional(readOnly = true)
  public boolean isSubscriptionActive(UUID parentProfileId) {
    if (!billingProperties.isEnforcementEnabled()) {
      return true;
    }
    if (emailVerificationProperties.isEnforcementEnabled() && !isEmailVerified(parentProfileId)) {
      return false;
    }
    Subscription sub =
        subscriptionRepository
            .findFirstByParentProfileIdOrderByCreatedAtDesc(parentProfileId)
            .orElse(null);
    if (sub == null) {
      return false;
    }
    return isActive(sub);
  }

  private boolean isTrialEligible(UUID parentProfileId) {
    if (!emailVerificationProperties.isEnforcementEnabled()) {
      return true;
    }
    return isEmailVerified(parentProfileId);
  }

  private boolean isEmailVerified(UUID parentProfileId) {
    return parentProfileRepository
        .findUserIdByProfileId(parentProfileId)
        .flatMap(userRepository::findById)
        .map(User::isEmailVerified)
        .orElse(false);
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
    Subscription sub =
        subscriptionRepository
            .findFirstByParentProfileIdOrderByCreatedAtDesc(parent.getId())
            .orElse(null);
    if (sub == null) {
      return pendingSubscriptionResponse(parent.getId());
    }
    return toResponse(sub);
  }

  private SubscriptionResponse pendingSubscriptionResponse(UUID parentProfileId) {
    boolean verified = isEmailVerified(parentProfileId);
    String status = verified ? "NONE" : "PENDING_EMAIL_VERIFICATION";
    return SubscriptionResponse.builder()
        .planId(SubscriptionPlan.FREE.getPlanId())
        .status(status)
        .active(false)
        .maxDevices(0)
        .liveStreamingAllowed(false)
        .build();
  }

  @Transactional
  public void cancelProviderSubscriptionForParent(UUID parentProfileId) {
    subscriptionRepository
        .findFirstByParentProfileIdOrderByCreatedAtDesc(parentProfileId)
        .ifPresent(
            sub -> {
              if (sub.getProviderSubscriptionId() != null
                  && !sub.getProviderSubscriptionId().isBlank()) {
                stripeCheckoutService.cancelSubscription(sub.getProviderSubscriptionId());
              }
              sub.setStatus(SubscriptionStatus.CANCELLED);
              subscriptionRepository.save(sub);
            });
  }

  @Transactional(readOnly = true)
  public List<PlanResponse> listPlans() {
    return Arrays.stream(SubscriptionPlan.values())
        .map(
            p ->
                PlanResponse.builder()
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
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CANNOT_CHECKOUT_FREE_PLAN);
    }
    String url = stripeCheckoutService.createCheckoutSession(parent.getId(), plan);
    auditService.log(
        AuditActionConstants.BILLING_CHECKOUT_STARTED,
        "plan:" + planId,
        null,
        Map.of("planId", planId));
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
    Subscription sub =
        subscriptionRepository
            .findFirstByParentProfileIdOrderByCreatedAtDesc(parentProfileId)
            .orElseGet(() -> Subscription.builder().parentProfileId(parentProfileId).build());
    sub.setPlanId(planId);
    sub.setStatus(SubscriptionStatus.ACTIVE);
    sub.setProvider(provider);
    sub.setProviderCustomerId(customerId);
    sub.setProviderSubscriptionId(subscriptionId);
    sub.setCurrentPeriodEnd(periodEnd);
    sub.setTrialEndsAt(null);
    subscriptionRepository.save(sub);
    auditService.log(
        AuditActionConstants.SUBSCRIPTION_ACTIVATED,
        "subscription:" + sub.getId(),
        null,
        Map.of("planId", planId, "provider", provider));
  }

  @Transactional
  public void recordPayment(
      UUID subscriptionId,
      long amountCents,
      String currency,
      String provider,
      String providerRef,
      String status) {
    if (paymentRepository.findByProviderRef(providerRef).isPresent()) {
      return;
    }
    paymentRepository.save(
        Payment.builder()
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
    subscriptionRepository
        .findByProviderSubscriptionId(providerSubscriptionId)
        .ifPresent(
            sub -> {
              sub.setStatus(SubscriptionStatus.CANCELLED);
              subscriptionRepository.save(sub);
              auditService.log(
                  AuditActionConstants.SUBSCRIPTION_CANCELLED,
                  "subscription:" + sub.getId(),
                  null,
                  Map.of());
            });
  }

  @Transactional
  public void markPastDue(String providerSubscriptionId, Instant periodEnd) {
    subscriptionRepository
        .findByProviderSubscriptionId(providerSubscriptionId)
        .ifPresent(
            sub -> {
              sub.setStatus(SubscriptionStatus.PAST_DUE);
              sub.setCurrentPeriodEnd(periodEnd);
              subscriptionRepository.save(sub);
            });
  }

  private ParentProfile requireParent() {
    UUID userId = SecurityUtils.requireUserId();
    return parentProfileRepository
        .findByUser_Id(userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));
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
        .liveStreamingAllowed(
            plan.isLiveStreamingAllowed() || sub.getStatus() == SubscriptionStatus.TRIAL)
        .build();
  }
}
