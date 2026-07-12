package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.ScreenTimeRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenTimeRuleRepository extends JpaRepository<ScreenTimeRule, UUID> {

  List<ScreenTimeRule> findByPolicyId(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
