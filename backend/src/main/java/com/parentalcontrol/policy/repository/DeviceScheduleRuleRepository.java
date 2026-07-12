package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.DeviceScheduleRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceScheduleRuleRepository extends JpaRepository<DeviceScheduleRule, UUID> {

  List<DeviceScheduleRule> findByPolicyId(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
