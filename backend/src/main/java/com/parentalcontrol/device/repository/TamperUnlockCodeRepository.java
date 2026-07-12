package com.parentalcontrol.device.repository;

import com.parentalcontrol.device.domain.TamperUnlockCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TamperUnlockCodeRepository extends JpaRepository<TamperUnlockCode, UUID> {

  Optional<TamperUnlockCode>
      findFirstByDeviceIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
          UUID deviceId, Instant now);

  @Modifying
  @Query(
      """
            UPDATE TamperUnlockCode t
            SET t.usedAt = :now, t.updatedAt = :now
            WHERE t.deviceId = :deviceId AND t.usedAt IS NULL
            """)
  void invalidateActiveForDevice(UUID deviceId, Instant now);
}
