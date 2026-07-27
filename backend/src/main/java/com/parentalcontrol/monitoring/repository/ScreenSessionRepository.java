package com.parentalcontrol.monitoring.repository;

import com.parentalcontrol.monitoring.domain.ScreenSession;
import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenSessionRepository extends JpaRepository<ScreenSession, UUID> {

  Optional<ScreenSession> findByIdAndStatus(UUID id, ScreenSessionStatus status);

  List<ScreenSession> findByDeviceIdAndStatusOrderByStartedAtDesc(
      UUID deviceId, ScreenSessionStatus status);

  Optional<ScreenSession> findFirstByDeviceIdAndStatusOrderByStartedAtDesc(
      UUID deviceId, ScreenSessionStatus status);

  Optional<ScreenSession> findFirstByDeviceIdAndStatusInOrderByStartedAtDesc(
      UUID deviceId, Collection<ScreenSessionStatus> statuses);

  long countByStatus(ScreenSessionStatus status);

  List<ScreenSession> findByDeviceIdAndStatusIn(UUID deviceId, Collection<ScreenSessionStatus> statuses);

  List<ScreenSession> findByStatusAndStartedAtBefore(ScreenSessionStatus status, Instant cutoff);
}
