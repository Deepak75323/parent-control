package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.AppUsageLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Data-access facade for AppUsageLog. Services depend on this interface; AppUsageLogDaoImpl
 * delegates to AppUsageLogRepository.
 */
public interface AppUsageLogDao {

  Optional<AppUsageLog> findById(UUID id);

  AppUsageLog save(AppUsageLog entity);

  <S extends AppUsageLog> List<S> saveAll(Iterable<S> entities);

  void delete(AppUsageLog entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends AppUsageLog> entities);

  boolean existsById(UUID id);

  long count();

  List<AppUsageLog> findAll();

  void flush();

  Page<AppUsageLog> findByDeviceIdOrderByRecordedAtDesc(UUID deviceId, Pageable pageable);
}
