package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.NotificationLog;
import com.parentalcontrol.monitoring.repository.NotificationLogRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationLogDaoImpl implements NotificationLogDao {

  private final NotificationLogRepository repository;

  @Override
  public Optional<NotificationLog> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public NotificationLog save(NotificationLog entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends NotificationLog> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(NotificationLog entity) {
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
  public void deleteAll(Iterable<? extends NotificationLog> entities) {
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
  public List<NotificationLog> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Page<NotificationLog> findByDeviceIdOrderByPostedAtDesc(UUID deviceId, Pageable pageable) {
    return repository.findByDeviceIdOrderByPostedAtDesc(deviceId, pageable);
  }
}
