package com.parentalcontrol.portal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "portal_operators")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalOperator {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "family_handle", nullable = false, length = 32)
  private String familyHandle;

  @Column(name = "granted_at", nullable = false)
  @Builder.Default
  private Instant grantedAt = Instant.now();

  @Column(name = "granted_by")
  private UUID grantedBy;
}
