package com.parentalcontrol.device.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pairing_codes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PairingCode extends BaseEntity {

  @Column(nullable = false, unique = true, length = 8)
  private String code;

  @Column(name = "parent_profile_id", nullable = false)
  private UUID parentProfileId;

  @Column(name = "child_profile_id")
  private UUID childProfileId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "device_id")
  private UUID deviceId;

  /** bcrypt hash of the child device login password (parent sets during pairing). */
  @Column(name = "child_login_password_hash")
  private String childLoginPasswordHash;

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isUsed() {
    return usedAt != null;
  }
}
