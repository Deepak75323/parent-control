package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.StreamingPolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StreamingPolicyRuleRepository extends JpaRepository<StreamingPolicyRule, UUID> {

    Optional<StreamingPolicyRule> findByPolicyId(UUID policyId);

    void deleteByPolicyId(UUID policyId);
}
