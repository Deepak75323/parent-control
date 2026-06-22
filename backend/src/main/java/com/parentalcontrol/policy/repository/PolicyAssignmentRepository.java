package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.PolicyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PolicyAssignmentRepository extends JpaRepository<PolicyAssignment, UUID> {

    List<PolicyAssignment> findByDeviceIdAndActiveTrue(UUID deviceId);

    List<PolicyAssignment> findByPolicyId(UUID policyId);

    @Query("""
            SELECT pa.policyId FROM PolicyAssignment pa
            WHERE pa.deviceId = :deviceId AND pa.active = true
            """)
    List<UUID> findActivePolicyIdsByDeviceId(UUID deviceId);
}
