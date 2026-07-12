package com.parentalcontrol.user.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import com.parentalcontrol.common.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(name = "email_verified", nullable = false)
  @Builder.Default
  private boolean emailVerified = false;

  @Column(name = "consent_accepted", nullable = false)
  @Builder.Default
  private boolean consentAccepted = false;

  @Column(name = "parent_pin_hash")
  private String parentPinHash;

  @Column(name = "silent_stream_pin_unlocked", nullable = false)
  @Builder.Default
  private boolean silentStreamPinUnlocked = false;

  /** Access/refresh tokens issued at or before this instant are rejected. */
  @Column(name = "tokens_invalidated_after")
  private java.time.Instant tokensInvalidatedAfter;
}
