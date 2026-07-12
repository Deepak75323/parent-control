package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.TimeExtensionRequest;
import com.parentalcontrol.policy.domain.TimeExtensionStatus;
import com.parentalcontrol.policy.repository.TimeExtensionRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TimeExtensionRequestDaoImpl implements TimeExtensionRequestDao {

  private final TimeExtensionRequestRepository repository;

  @Override
  public Optional<TimeExtensionRequest> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public TimeExtensionRequest save(TimeExtensionRequest entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends TimeExtensionRequest> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(TimeExtensionRequest entity) {
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
  public void deleteAll(Iterable<? extends TimeExtensionRequest> entities) {
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
  public List<TimeExtensionRequest> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<TimeExtensionRequest> findByParentProfileIdAndStatusOrderByCreatedAtDesc(
      UUID parentProfileId, TimeExtensionStatus status) {
    return repository.findByParentProfileIdAndStatusOrderByCreatedAtDesc(parentProfileId, status);
  }

  @Override
  public List<TimeExtensionRequest> findByDeviceIdAndStatusAndGrantedUntilAfter(
      UUID deviceId, TimeExtensionStatus status, Instant now) {
    return repository.findByDeviceIdAndStatusAndGrantedUntilAfter(deviceId, status, now);
  }

  @Override
  public List<TimeExtensionRequest> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId) {
    return repository.findByDeviceIdOrderByCreatedAtDesc(deviceId);
  }
}
