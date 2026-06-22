package com.parentalcontrol.device.repository;

import com.parentalcontrol.device.domain.DeviceProvisioningEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceProvisioningEventRepository extends JpaRepository<DeviceProvisioningEvent, UUID> {
}
