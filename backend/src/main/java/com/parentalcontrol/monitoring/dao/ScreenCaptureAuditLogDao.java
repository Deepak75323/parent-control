package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.ScreenCaptureAuditLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for ScreenCaptureAuditLog. Services depend on this interface;
 * ScreenCaptureAuditLogDaoImpl delegates to ScreenCaptureAuditLogRepository.
 */
public interface ScreenCaptureAuditLogDao {

  Optional<ScreenCaptureAuditLog> findById(UUID id);

  ScreenCaptureAuditLog save(ScreenCaptureAuditLog entity);

  <S extends ScreenCaptureAuditLog> List<S> saveAll(Iterable<S> entities);

  void delete(ScreenCaptureAuditLog entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends ScreenCaptureAuditLog> entities);

  boolean existsById(UUID id);

  long count();

  List<ScreenCaptureAuditLog> findAll();

  void flush();

  Optional<ScreenCaptureAuditLog> findFirstBySessionIdAndEndedAtIsNull(UUID sessionId);
}
