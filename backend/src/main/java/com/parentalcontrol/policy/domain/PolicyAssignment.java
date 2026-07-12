package com.parentalcontrol.policy.domain;

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
@Table(name = "policy_assignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAssignment extends BaseEntity {

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  @Column(name = "device_id", nullable = false)
  private UUID deviceId;

  @Builder.Default
  @Column(nullable = false)
  private Boolean active = true;
}
