package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.LocationHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Data-access facade for LocationHistory. Services depend on this interface; LocationHistoryDaoImpl
 * delegates to LocationHistoryRepository.
 */
public interface LocationHistoryDao {

  Optional<LocationHistory> findById(UUID id);

  LocationHistory save(LocationHistory entity);

  <S extends LocationHistory> List<S> saveAll(Iterable<S> entities);

  void delete(LocationHistory entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends LocationHistory> entities);

  boolean existsById(UUID id);

  long count();

  List<LocationHistory> findAll();

  void flush();

  Page<LocationHistory> findByDeviceIdOrderByRecordedAtDesc(UUID deviceId, Pageable pageable);
}
