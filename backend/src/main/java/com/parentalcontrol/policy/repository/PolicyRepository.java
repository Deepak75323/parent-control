package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.Policy;
import com.parentalcontrol.policy.domain.PolicyType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

  List<Policy> findByParentProfileIdOrderByUpdatedAtDesc(UUID parentProfileId);

  List<Policy> findByParentProfileIdAndPolicyTypeOrderByUpdatedAtDesc(
      UUID parentProfileId, PolicyType policyType);
}
