package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.AppUsageLog;
import com.parentalcontrol.monitoring.repository.AppUsageLogRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AppUsageLogDaoImpl implements AppUsageLogDao {

  private final AppUsageLogRepository repository;

  @Override
  public Optional<AppUsageLog> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public AppUsageLog save(AppUsageLog entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends AppUsageLog> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(AppUsageLog entity) {
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
  public void deleteAll(Iterable<? extends AppUsageLog> entities) {
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
  public List<AppUsageLog> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Page<AppUsageLog> findByDeviceIdOrderByRecordedAtDesc(UUID deviceId, Pageable pageable) {
    return repository.findByDeviceIdOrderByRecordedAtDesc(deviceId, pageable);
  }
}
