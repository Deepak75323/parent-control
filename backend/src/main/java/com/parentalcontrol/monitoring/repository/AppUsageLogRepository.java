package com.parentalcontrol.monitoring.repository;

import com.parentalcontrol.monitoring.domain.AppUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppUsageLogRepository extends JpaRepository<AppUsageLog, UUID> {

    Page<AppUsageLog> findByDeviceIdOrderByRecordedAtDesc(UUID deviceId, Pageable pageable);
}
