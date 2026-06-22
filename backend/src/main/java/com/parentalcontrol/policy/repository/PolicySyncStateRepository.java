package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.PolicySyncState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PolicySyncStateRepository extends JpaRepository<PolicySyncState, UUID> {

    Optional<PolicySyncState> findByDeviceId(UUID deviceId);
}
