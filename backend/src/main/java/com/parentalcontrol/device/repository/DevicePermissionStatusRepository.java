package com.parentalcontrol.device.repository;

import com.parentalcontrol.device.domain.DevicePermissionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevicePermissionStatusRepository
    extends JpaRepository<DevicePermissionStatus, UUID> {

  Optional<DevicePermissionStatus> findByDeviceId(UUID deviceId);
}
