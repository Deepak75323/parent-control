package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.Alert;
import com.parentalcontrol.monitoring.repository.AlertRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlertDaoImpl implements AlertDao {

  private final AlertRepository repository;

  @Override
  public Optional<Alert> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public Alert save(Alert entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends Alert> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(Alert entity) {
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
  public void deleteAll(Iterable<? extends Alert> entities) {
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
  public List<Alert> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Page<Alert> findByDeviceIdInOrderByCreatedAtDesc(
      Collection<UUID> deviceIds, Pageable pageable) {
    return repository.findByDeviceIdInOrderByCreatedAtDesc(deviceIds, pageable);
  }
}
