package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.ScreenCaptureAuditLog;
import com.parentalcontrol.monitoring.repository.ScreenCaptureAuditLogRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScreenCaptureAuditLogDaoImpl implements ScreenCaptureAuditLogDao {

  private final ScreenCaptureAuditLogRepository repository;

  @Override
  public Optional<ScreenCaptureAuditLog> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public ScreenCaptureAuditLog save(ScreenCaptureAuditLog entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends ScreenCaptureAuditLog> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(ScreenCaptureAuditLog entity) {
    repository.delete(entity);
  }

  @Override
  public void deleteById(UUID id) {
    repository.deleteById(id);
  }

  @Override
  public void deleteAll() {
    repository.deleteAll();
  }

  @Override
  public void deleteAll(Iterable<? extends ScreenCaptureAuditLog> entities) {
    repository.deleteAll(entities);
  }

  @Override
  public boolean existsById(UUID id) {
    return repository.existsById(id);
  }

  @Override
  public long count() {
    return repository.count();
  }

  @Override
  public List<ScreenCaptureAuditLog> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<ScreenCaptureAuditLog> findFirstBySessionIdAndEndedAtIsNull(UUID sessionId) {
    return repository.findFirstBySessionIdAndEndedAtIsNull(sessionId);
  }
}
