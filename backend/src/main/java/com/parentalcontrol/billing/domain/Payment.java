package com.parentalcontrol.billing.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends BaseEntity {

  @Column(name = "subscription_id", nullable = false)
  private UUID subscriptionId;

  @Column(name = "amount_cents", nullable = false)
  private long amountCents;

  @Column(nullable = false)
  @Builder.Default
  private String currency = "USD";

  private String provider;

  @Column(name = "provider_ref")
  private String providerRef;

  @Column(nullable = false)
  private String status;
}
