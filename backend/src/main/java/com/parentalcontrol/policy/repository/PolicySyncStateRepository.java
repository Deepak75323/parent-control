package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.PolicySyncState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicySyncStateRepository extends JpaRepository<PolicySyncState, UUID> {

  Optional<PolicySyncState> findByDeviceId(UUID deviceId);
}
