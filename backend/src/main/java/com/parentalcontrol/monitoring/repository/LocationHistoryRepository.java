package com.parentalcontrol.monitoring.repository;

import com.parentalcontrol.monitoring.domain.LocationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LocationHistoryRepository extends JpaRepository<LocationHistory, UUID> {

    Page<LocationHistory> findByDeviceIdOrderByRecordedAtDesc(UUID deviceId, Pageable pageable);
}
