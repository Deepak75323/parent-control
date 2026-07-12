package com.parentalcontrol.device.repository;

import com.parentalcontrol.device.domain.DeviceProvisioningEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceProvisioningEventRepository
    extends JpaRepository<DeviceProvisioningEvent, UUID> {}
