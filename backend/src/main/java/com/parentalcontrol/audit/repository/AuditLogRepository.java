package com.parentalcontrol.audit.repository;

import com.parentalcontrol.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.userId = :parentUserId
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findForParent(@Param("parentUserId") UUID parentUserId, Pageable pageable);

    Page<AuditLog> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId, Pageable pageable);
}
