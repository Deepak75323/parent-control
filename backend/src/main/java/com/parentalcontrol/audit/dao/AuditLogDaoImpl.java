package com.parentalcontrol.audit.dao;

import com.parentalcontrol.audit.domain.AuditLog;
import com.parentalcontrol.audit.repository.AuditLogRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditLogDaoImpl implements AuditLogDao {

  private final AuditLogRepository repository;

  @Override
  public Optional<AuditLog> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public AuditLog save(AuditLog entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends AuditLog> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(AuditLog entity) {
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
  public void deleteAll(Iterable<? extends AuditLog> entities) {
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
  public List<AuditLog> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Page<AuditLog> findForParent(UUID parentUserId, Pageable pageable) {
    return repository.findForParent(parentUserId, pageable);
  }

  @Override
  public Page<AuditLog> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId, Pageable pageable) {
    return repository.findByDeviceIdOrderByCreatedAtDesc(deviceId, pageable);
  }
}
