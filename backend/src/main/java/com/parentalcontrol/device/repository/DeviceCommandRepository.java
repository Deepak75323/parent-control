package com.parentalcontrol.device.repository;

import com.parentalcontrol.device.domain.DeviceCommand;
import com.parentalcontrol.device.domain.DeviceCommandStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, UUID> {

  List<DeviceCommand> findByDeviceIdAndStatusOrderByCreatedAtAsc(
      UUID deviceId, DeviceCommandStatus status);

  /**
   * Requeue stale DELIVERED commands that still have retry budget (DELIVERED → PENDING). Commands
   * with an execution fingerprint are never requeued — only ACK retry applies.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      """
            UPDATE DeviceCommand c SET c.status = 'PENDING', c.deliveredAt = null
            WHERE c.deviceId = :deviceId
              AND c.status = 'DELIVERED'
              AND c.executedAt IS NULL
              AND c.deliveredAt < :cutoff
              AND c.deliveryAttemptCount < :maxAttempts
            """)
  int requeueStaleDelivered(
      @Param("deviceId") UUID deviceId,
      @Param("cutoff") Instant cutoff,
      @Param("maxAttempts") int maxAttempts);

  /**
   * Permanently fail stale DELIVERED commands that exhausted retry budget. Only applies when
   * execution was never recorded on the device.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      """
            UPDATE DeviceCommand c SET c.status = 'FAILED',
                c.errorMessage = 'Delivery timeout after max retries',
                c.completedAt = :now
            WHERE c.deviceId = :deviceId
              AND c.status = 'DELIVERED'
              AND c.executedAt IS NULL
              AND c.deliveredAt < :cutoff
              AND c.deliveryAttemptCount >= :maxAttempts
            """)
  int failExhaustedStaleDelivered(
      @Param("deviceId") UUID deviceId,
      @Param("cutoff") Instant cutoff,
      @Param("maxAttempts") int maxAttempts,
      @Param("now") Instant now);

  /** Record execution fingerprint (idempotent — first write wins). */
  @Modifying(clearAutomatically = true)
  @Query(
      """
            UPDATE DeviceCommand c SET c.executedAt = :now,
                c.executionSuccess = :success,
                c.executionError = :errorMessage
            WHERE c.id = :commandId
              AND c.deviceId = :deviceId
              AND c.status = 'DELIVERED'
              AND c.executedAt IS NULL
            """)
  int recordExecutionFingerprint(
      @Param("commandId") UUID commandId,
      @Param("deviceId") UUID deviceId,
      @Param("now") Instant now,
      @Param("success") boolean success,
      @Param("errorMessage") String errorMessage);

  List<DeviceCommand> findByDeviceIdAndStatusAndExecutedAtIsNotNullAndExecutedAtBefore(
      UUID deviceId, DeviceCommandStatus status, Instant executedBefore);

  /** DELIVERED but not yet execution-fingerprinted — for device crash recovery. */
  List<DeviceCommand> findByDeviceIdAndStatusAndExecutedAtIsNullOrderByDeliveredAtAsc(
      UUID deviceId, DeviceCommandStatus status);

  /** Atomically claim a pending command (PENDING → DELIVERED) and increment attempt count. */
  @Modifying(clearAutomatically = true)
  @Query(
      """
            UPDATE DeviceCommand c SET c.status = 'DELIVERED', c.deliveredAt = :now,
                c.deliveryAttemptCount = c.deliveryAttemptCount + 1
            WHERE c.id = :commandId
              AND c.deviceId = :deviceId
              AND c.status = 'PENDING'
              AND c.deliveryAttemptCount < :maxAttempts
            """)
  int claimPendingCommand(
      @Param("commandId") UUID commandId,
      @Param("deviceId") UUID deviceId,
      @Param("now") Instant now,
      @Param("maxAttempts") int maxAttempts);

  /** Finish a successfully executed command (DELIVERED → COMPLETED). */
  @Modifying(clearAutomatically = true)
  @Query(
      """
            UPDATE DeviceCommand c SET c.status = 'COMPLETED', c.completedAt = :now, c.errorMessage = null
            WHERE c.id = :commandId
              AND c.deviceId = :deviceId
              AND c.status = 'DELIVERED'
            """)
  int completeDeliveredCommand(
      @Param("commandId") UUID commandId,
      @Param("deviceId") UUID deviceId,
      @Param("now") Instant now);

  /** Finish a failed command execution (DELIVERED → FAILED). */
  @Modifying(clearAutomatically = true)
  @Query(
      """
            UPDATE DeviceCommand c SET c.status = 'FAILED', c.completedAt = :now, c.errorMessage = :errorMessage
            WHERE c.id = :commandId
              AND c.deviceId = :deviceId
              AND c.status = 'DELIVERED'
            """)
  int failDeliveredCommand(
      @Param("commandId") UUID commandId,
      @Param("deviceId") UUID deviceId,
      @Param("now") Instant now,
      @Param("errorMessage") String errorMessage);
}
