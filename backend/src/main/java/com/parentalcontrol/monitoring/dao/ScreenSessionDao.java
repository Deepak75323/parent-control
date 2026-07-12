package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.ScreenSession;
import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for ScreenSession. Services depend on this interface; ScreenSessionDaoImpl
 * delegates to ScreenSessionRepository.
 */
public interface ScreenSessionDao {

  Optional<ScreenSession> findById(UUID id);

  ScreenSession save(ScreenSession entity);

  <S extends ScreenSession> List<S> saveAll(Iterable<S> entities);

  void delete(ScreenSession entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends ScreenSession> entities);

  boolean existsById(UUID id);

  long count();

  List<ScreenSession> findAll();

  void flush();

  Optional<ScreenSession> findByIdAndStatus(UUID id, ScreenSessionStatus status);

  List<ScreenSession> findByDeviceIdAndStatusOrderByStartedAtDesc(
      UUID deviceId, ScreenSessionStatus status);

  Optional<ScreenSession> findFirstByDeviceIdAndStatusOrderByStartedAtDesc(
      UUID deviceId, ScreenSessionStatus status);

  Optional<ScreenSession> findFirstByDeviceIdAndStatusInOrderByStartedAtDesc(
      UUID deviceId, Collection<ScreenSessionStatus> statuses);

  long countByStatus(ScreenSessionStatus status);
}
