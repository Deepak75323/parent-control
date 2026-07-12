package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.PolicySyncState;
import com.parentalcontrol.policy.repository.PolicySyncStateRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PolicySyncStateDaoImpl implements PolicySyncStateDao {

  private final PolicySyncStateRepository repository;

  @Override
  public Optional<PolicySyncState> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public PolicySyncState save(PolicySyncState entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends PolicySyncState> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(PolicySyncState entity) {
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
  public void deleteAll(Iterable<? extends PolicySyncState> entities) {
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
  public List<PolicySyncState> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<PolicySyncState> findByDeviceId(UUID deviceId) {
    return repository.findByDeviceId(deviceId);
  }
}
