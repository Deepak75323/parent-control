package com.parentalcontrol.monitoring.repository;

import com.parentalcontrol.monitoring.domain.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByDeviceIdOrderByPostedAtDesc(UUID deviceId, Pageable pageable);
}
