package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.ScreenSession;
import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import com.parentalcontrol.monitoring.repository.ScreenSessionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScreenSessionDaoImpl implements ScreenSessionDao {

  private final ScreenSessionRepository repository;

  @Override
  public Optional<ScreenSession> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public ScreenSession save(ScreenSession entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends ScreenSession> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(ScreenSession entity) {
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
  public void deleteAll(Iterable<? extends ScreenSession> entities) {
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
  public List<ScreenSession> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<ScreenSession> findByIdAndStatus(UUID id, ScreenSessionStatus status) {
    return repository.findByIdAndStatus(id, status);
  }

  @Override
  public List<ScreenSession> findByDeviceIdAndStatusOrderByStartedAtDesc(
      UUID deviceId, ScreenSessionStatus status) {
    return repository.findByDeviceIdAndStatusOrderByStartedAtDesc(deviceId, status);
  }

  @Override
  public Optional<ScreenSession> findFirstByDeviceIdAndStatusOrderByStartedAtDesc(
      UUID deviceId, ScreenSessionStatus status) {
    return repository.findFirstByDeviceIdAndStatusOrderByStartedAtDesc(deviceId, status);
  }

  @Override
  public Optional<ScreenSession> findFirstByDeviceIdAndStatusInOrderByStartedAtDesc(
      UUID deviceId, Collection<ScreenSessionStatus> statuses) {
    return repository.findFirstByDeviceIdAndStatusInOrderByStartedAtDesc(deviceId, statuses);
  }

  @Override
  public long countByStatus(ScreenSessionStatus status) {
    return repository.countByStatus(status);
  }
}
