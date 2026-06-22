package com.parentalcontrol.device.repository;

import com.parentalcontrol.device.domain.PairingCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PairingCodeRepository extends JpaRepository<PairingCode, UUID> {

    Optional<PairingCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /**
     * Atomically marks a pairing code as used. Returns 1 on success, 0 if already used or expired.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PairingCode p
            SET p.usedAt = :usedAt, p.deviceId = :deviceId
            WHERE p.id = :id AND p.usedAt IS NULL AND p.expiresAt > :now
            """)
    int claimCode(
            @Param("id") UUID id,
            @Param("deviceId") UUID deviceId,
            @Param("usedAt") Instant usedAt,
            @Param("now") Instant now);
}
