package com.parentalcontrol.user.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "parent_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentProfile extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column private String phone;

  @Column(name = "timezone")
  @Builder.Default
  private String timezone = "UTC";

  /** Bcrypt hash — same password every child uses to open the child app. */
  @Column(name = "default_child_login_password_hash")
  private String defaultChildLoginPasswordHash;

  /**
   * Unique public family handle (lowercase, no leading @). Child first-time login uses handle +
   * family password to identify this parent without ambiguous password-only matching.
   */
  @Column(name = "family_handle", length = 32)
  private String familyHandle;

  public UUID getUserId() {
    return user != null ? user.getId() : null;
  }
}
