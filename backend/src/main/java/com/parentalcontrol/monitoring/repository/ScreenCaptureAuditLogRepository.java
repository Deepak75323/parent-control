package com.parentalcontrol.monitoring.repository;

import com.parentalcontrol.monitoring.domain.ScreenCaptureAuditLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenCaptureAuditLogRepository
    extends JpaRepository<ScreenCaptureAuditLog, UUID> {

  Optional<ScreenCaptureAuditLog> findFirstBySessionIdAndEndedAtIsNull(UUID sessionId);
}
