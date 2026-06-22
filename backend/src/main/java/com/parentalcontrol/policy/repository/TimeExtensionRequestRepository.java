package com.parentalcontrol.policy.repository;

import com.parentalcontrol.policy.domain.TimeExtensionRequest;
import com.parentalcontrol.policy.domain.TimeExtensionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimeExtensionRequestRepository extends JpaRepository<TimeExtensionRequest, UUID> {

    List<TimeExtensionRequest> findByParentProfileIdAndStatusOrderByCreatedAtDesc(
            UUID parentProfileId,
            TimeExtensionStatus status);

    List<TimeExtensionRequest> findByDeviceIdAndStatusAndGrantedUntilAfter(
            UUID deviceId,
            TimeExtensionStatus status,
            Instant now);

    List<TimeExtensionRequest> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId);
}
