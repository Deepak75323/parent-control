package com.parentalcontrol.billing.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseEntity {

  @Column(name = "parent_profile_id", nullable = false)
  private UUID parentProfileId;

  @Column(name = "plan_id", nullable = false)
  private String planId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SubscriptionStatus status;

  @Column(name = "trial_ends_at")
  private Instant trialEndsAt;

  @Column(name = "current_period_end")
  private Instant currentPeriodEnd;

  @Column private String provider;

  @Column(name = "provider_customer_id")
  private String providerCustomerId;

  @Column(name = "provider_subscription_id")
  private String providerSubscriptionId;
}
