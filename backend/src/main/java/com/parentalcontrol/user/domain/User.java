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

  /** Base64 salt for child offline PBKDF2 parent-password verify. */
  @Column(name = "parent_offline_pin_salt")
  private String parentOfflinePinSalt;

  /** Base64 PBKDF2 hash for child offline parent-password verify. */
  @Column(name = "parent_offline_pin_hash")
  private String parentOfflinePinHash;

  @Column(name = "parent_offline_pin_iterations")
  private Integer parentOfflinePinIterations;

  @Column(name = "silent_stream_pin_unlocked", nullable = false)
  @Builder.Default
  private boolean silentStreamPinUnlocked = false;

  /** Bcrypt hash for reviewing soft-deleted Notes (owner view). */
  @Column(name = "deleted_messages_pin_hash")
  private String deletedMessagesPinHash;

  /** Session flag: parent entered deleted-messages PIN this login period. */
  @Column(name = "deleted_messages_pin_unlocked", nullable = false)
  @Builder.Default
  private boolean deletedMessagesPinUnlocked = false;

  /** Bcrypt hash for camera live-view unlock (separate from the chat decrypt code). */
  @Column(name = "camera_password_hash")
  private String cameraPasswordHash;

  /** Access/refresh tokens issued at or before this instant are rejected. */
  @Column(name = "tokens_invalidated_after")
  private java.time.Instant tokensInvalidatedAfter;
}
