package com.parentalcontrol.monitoring.repository;

import com.parentalcontrol.monitoring.domain.Alert;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

  Page<Alert> findByDeviceIdInOrderByCreatedAtDesc(Collection<UUID> deviceIds, Pageable pageable);
}
