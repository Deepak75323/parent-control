package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.AppBlockRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppBlockRuleRepository extends JpaRepository<AppBlockRule, UUID> {

  List<AppBlockRule> findByPolicyId(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
