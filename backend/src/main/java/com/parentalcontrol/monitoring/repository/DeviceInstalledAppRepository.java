package com.parentalcontrol.monitoring.repository;

import com.parentalcontrol.monitoring.domain.DeviceInstalledApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeviceInstalledAppRepository extends JpaRepository<DeviceInstalledApp, UUID> {

    List<DeviceInstalledApp> findByDeviceIdOrderByAppLabelAsc(UUID deviceId);

    @Modifying
    @Query("UPDATE DeviceInstalledApp d SET d.deletedAt = CURRENT_TIMESTAMP WHERE d.deviceId = :deviceId AND d.deletedAt IS NULL")
    void softDeleteAllByDeviceId(@Param("deviceId") UUID deviceId);

    /** Full resync replaces the installed-app list; hard delete avoids UNIQUE(device_id, package_name) conflicts. */
    @Modifying
    @Query("DELETE FROM DeviceInstalledApp d WHERE d.deviceId = :deviceId")
    void deleteAllByDeviceId(@Param("deviceId") UUID deviceId);
}
