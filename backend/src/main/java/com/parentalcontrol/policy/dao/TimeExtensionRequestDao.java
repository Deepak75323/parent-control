package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.TimeExtensionRequest;
import com.parentalcontrol.policy.domain.TimeExtensionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for TimeExtensionRequest. Services depend on this interface;
 * TimeExtensionRequestDaoImpl delegates to TimeExtensionRequestRepository.
 */
public interface TimeExtensionRequestDao {

  Optional<TimeExtensionRequest> findById(UUID id);

  TimeExtensionRequest save(TimeExtensionRequest entity);

  <S extends TimeExtensionRequest> List<S> saveAll(Iterable<S> entities);

  void delete(TimeExtensionRequest entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends TimeExtensionRequest> entities);

  boolean existsById(UUID id);

  long count();

  List<TimeExtensionRequest> findAll();

  void flush();

  List<TimeExtensionRequest> findByParentProfileIdAndStatusOrderByCreatedAtDesc(
      UUID parentProfileId, TimeExtensionStatus status);

  List<TimeExtensionRequest> findByDeviceIdAndStatusAndGrantedUntilAfter(
      UUID deviceId, TimeExtensionStatus status, Instant now);

  List<TimeExtensionRequest> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId);
}
