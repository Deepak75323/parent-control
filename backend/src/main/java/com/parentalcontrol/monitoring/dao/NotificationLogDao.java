package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.NotificationLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Data-access facade for NotificationLog. Services depend on this interface; NotificationLogDaoImpl
 * delegates to NotificationLogRepository.
 */
public interface NotificationLogDao {

  Optional<NotificationLog> findById(UUID id);

  NotificationLog save(NotificationLog entity);

  <S extends NotificationLog> List<S> saveAll(Iterable<S> entities);

  void delete(NotificationLog entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends NotificationLog> entities);

  boolean existsById(UUID id);

  long count();

  List<NotificationLog> findAll();

  void flush();

  Page<NotificationLog> findByDeviceIdOrderByPostedAtDesc(UUID deviceId, Pageable pageable);
}
