package com.parentalcontrol.monitoring.repository;

import com.parentalcontrol.monitoring.domain.LocationHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationHistoryRepository extends JpaRepository<LocationHistory, UUID> {

  Page<LocationHistory> findByDeviceIdOrderByRecordedAtDesc(UUID deviceId, Pageable pageable);
}
