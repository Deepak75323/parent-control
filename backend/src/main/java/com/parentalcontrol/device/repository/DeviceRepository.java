package com.parentalcontrol.device.repository;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

  List<Device> findByParentProfileIdOrderByCreatedAtDesc(UUID parentProfileId);

  @Query(
      "SELECT d FROM Device d WHERE d.parentProfileId = :parentProfileId AND d.deletedAt IS NULL ORDER BY d.createdAt DESC")
  List<Device> findActiveByParentProfileIdOrderByCreatedAtDesc(
      @Param("parentProfileId") UUID parentProfileId);

  @Query("SELECT d FROM Device d WHERE d.deletedAt IS NULL")
  List<Device> findAllActive();

  @Query("SELECT d FROM Device d WHERE d.androidId = :androidId AND d.deletedAt IS NULL")
  Optional<Device> findByAndroidId(@Param("androidId") String androidId);

  @Query(
      "SELECT COUNT(d) FROM Device d WHERE d.parentProfileId = :parentProfileId AND d.deletedAt IS NULL")
  long countByParentProfileId(@Param("parentProfileId") UUID parentProfileId);

  /** Devices that consume a subscription seat (monitoring link still active). */
  @Query(
      "SELECT COUNT(d) FROM Device d WHERE d.parentProfileId = :parentProfileId"
          + " AND d.deletedAt IS NULL AND d.status <> :revoked")
  long countNonRevokedByParentProfileId(
      @Param("parentProfileId") UUID parentProfileId, @Param("revoked") DeviceStatus revoked);

  List<Device> findByStatusAndLastSeenAtBefore(DeviceStatus status, Instant cutoff);
}
