package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.Policy;
import com.parentalcontrol.policy.domain.PolicyType;
import com.parentalcontrol.policy.repository.PolicyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PolicyDaoImpl implements PolicyDao {

  private final PolicyRepository repository;

  @Override
  public Optional<Policy> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public Policy save(Policy entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends Policy> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(Policy entity) {
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
  public void deleteAll(Iterable<? extends Policy> entities) {
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
  public List<Policy> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<Policy> findByParentProfileIdOrderByUpdatedAtDesc(UUID parentProfileId) {
    return repository.findByParentProfileIdOrderByUpdatedAtDesc(parentProfileId);
  }

  @Override
  public List<Policy> findByParentProfileIdAndPolicyTypeOrderByUpdatedAtDesc(
      UUID parentProfileId, PolicyType policyType) {
    return repository.findByParentProfileIdAndPolicyTypeOrderByUpdatedAtDesc(
        parentProfileId, policyType);
  }

  @Override
  public List<Policy> findAllById(Iterable<UUID> ids) {
    return repository.findAllById(ids);
  }
}
