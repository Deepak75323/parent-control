package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.GeofenceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GeofenceRuleRepository extends JpaRepository<GeofenceRule, UUID> {

    List<GeofenceRule> findByPolicyId(UUID policyId);

    void deleteByPolicyId(UUID policyId);
}
