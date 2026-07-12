package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.StreamingPolicyRule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreamingPolicyRuleRepository extends JpaRepository<StreamingPolicyRule, UUID> {

  Optional<StreamingPolicyRule> findByPolicyId(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
