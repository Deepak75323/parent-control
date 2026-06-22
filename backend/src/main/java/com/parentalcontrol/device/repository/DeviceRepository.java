package com.parentalcontrol.device.repository;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findByParentProfileIdOrderByCreatedAtDesc(UUID parentProfileId);

    @Query("SELECT d FROM Device d WHERE d.androidId = :androidId AND d.deletedAt IS NULL")
    Optional<Device> findByAndroidId(@Param("androidId") String androidId);

    @Query("SELECT COUNT(d) FROM Device d WHERE d.parentProfileId = :parentProfileId AND d.deletedAt IS NULL")
    long countByParentProfileId(@Param("parentProfileId") UUID parentProfileId);

    List<Device> findByStatusAndLastSeenAtBefore(DeviceStatus status, Instant cutoff);
}
