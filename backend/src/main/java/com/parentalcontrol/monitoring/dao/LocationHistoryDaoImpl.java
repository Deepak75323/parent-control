package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.LocationHistory;
import com.parentalcontrol.monitoring.repository.LocationHistoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LocationHistoryDaoImpl implements LocationHistoryDao {

  private final LocationHistoryRepository repository;

  @Override
  public Optional<LocationHistory> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public LocationHistory save(LocationHistory entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends LocationHistory> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(LocationHistory entity) {
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
  public void deleteAll(Iterable<? extends LocationHistory> entities) {
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
  public List<LocationHistory> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Page<LocationHistory> findByDeviceIdOrderByRecordedAtDesc(
      UUID deviceId, Pageable pageable) {
    return repository.findByDeviceIdOrderByRecordedAtDesc(deviceId, pageable);
  }
}
