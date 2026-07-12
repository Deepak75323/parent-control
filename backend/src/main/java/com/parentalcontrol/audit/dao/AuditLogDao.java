package com.parentalcontrol.audit.dao;

import com.parentalcontrol.audit.domain.AuditLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Data-access facade for AuditLog. Services depend on this interface; AuditLogDaoImpl delegates to
 * AuditLogRepository.
 */
public interface AuditLogDao {

  Optional<AuditLog> findById(UUID id);

  AuditLog save(AuditLog entity);

  <S extends AuditLog> List<S> saveAll(Iterable<S> entities);

  void delete(AuditLog entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends AuditLog> entities);

  boolean existsById(UUID id);

  long count();

  List<AuditLog> findAll();

  void flush();

  Page<AuditLog> findForParent(UUID parentUserId, Pageable pageable);

  Page<AuditLog> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId, Pageable pageable);
}
