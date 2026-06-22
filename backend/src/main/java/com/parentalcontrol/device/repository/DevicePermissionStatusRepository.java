package com.parentalcontrol.device.repository;

import com.parentalcontrol.device.domain.DevicePermissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DevicePermissionStatusRepository extends JpaRepository<DevicePermissionStatus, UUID> {

    Optional<DevicePermissionStatus> findByDeviceId(UUID deviceId);
}
