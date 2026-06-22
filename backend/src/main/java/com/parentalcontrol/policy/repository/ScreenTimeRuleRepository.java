package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.ScreenTimeRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScreenTimeRuleRepository extends JpaRepository<ScreenTimeRule, UUID> {

    List<ScreenTimeRule> findByPolicyId(UUID policyId);

    void deleteByPolicyId(UUID policyId);
}
