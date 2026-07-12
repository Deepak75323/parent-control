package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.GeofenceRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeofenceRuleRepository extends JpaRepository<GeofenceRule, UUID> {

  List<GeofenceRule> findByPolicyId(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
