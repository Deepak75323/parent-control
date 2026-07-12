package com.parentalcontrol.device.repository;

import com.parentalcontrol.device.domain.PairingCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PairingCodeRepository extends JpaRepository<PairingCode, UUID> {

  Optional<PairingCode> findByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCase(String code);

  /**
   * Atomically marks a pairing code as used. Returns 1 on success, 0 if already used or expired.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
            UPDATE PairingCode p
            SET p.usedAt = :usedAt, p.deviceId = :deviceId
            WHERE p.id = :id AND p.usedAt IS NULL AND p.expiresAt > :now
            """)
  int claimCode(
      @Param("id") UUID id,
      @Param("deviceId") UUID deviceId,
      @Param("usedAt") Instant usedAt,
      @Param("now") Instant now);

  @Query(
      """
            SELECT p FROM PairingCode p
            WHERE p.usedAt IS NULL AND p.expiresAt > :now AND p.childLoginPasswordHash IS NOT NULL
            """)
  java.util.List<PairingCode> findActiveUnusedWithPassword(@Param("now") Instant now);

  /** Expire unused active codes for a parent so only the newest setup session remains. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
            UPDATE PairingCode p
            SET p.expiresAt = :now
            WHERE p.parentProfileId = :parentProfileId
              AND p.usedAt IS NULL
              AND p.expiresAt > :now
            """)
  int expireActiveUnusedForParent(
      @Param("parentProfileId") UUID parentProfileId, @Param("now") Instant now);
}
