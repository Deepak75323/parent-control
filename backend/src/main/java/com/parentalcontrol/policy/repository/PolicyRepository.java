package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.Policy;
import com.parentalcontrol.policy.domain.PolicyType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

  @Query(
      "SELECT p FROM Policy p WHERE p.parentProfileId = :parentProfileId "
          + "AND p.deletedAt IS NULL AND p.enabled = true "
          + "ORDER BY p.updatedAt DESC")
  List<Policy> findByParentProfileIdOrderByUpdatedAtDesc(
      @Param("parentProfileId") UUID parentProfileId);

  @Query(
      "SELECT p FROM Policy p WHERE p.parentProfileId = :parentProfileId "
          + "AND p.policyType = :policyType AND p.deletedAt IS NULL AND p.enabled = true "
          + "ORDER BY p.updatedAt DESC")
  List<Policy> findByParentProfileIdAndPolicyTypeOrderByUpdatedAtDesc(
      @Param("parentProfileId") UUID parentProfileId, @Param("policyType") PolicyType policyType);
}
