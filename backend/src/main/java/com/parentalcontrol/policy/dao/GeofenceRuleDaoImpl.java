package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.GeofenceRule;
import com.parentalcontrol.policy.repository.GeofenceRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GeofenceRuleDaoImpl implements GeofenceRuleDao {

  private final GeofenceRuleRepository repository;

  @Override
  public Optional<GeofenceRule> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public GeofenceRule save(GeofenceRule entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends GeofenceRule> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(GeofenceRule entity) {
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
  public void deleteAll(Iterable<? extends GeofenceRule> entities) {
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
  public List<GeofenceRule> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<GeofenceRule> findByPolicyId(UUID policyId) {
    return repository.findByPolicyId(policyId);
  }

  @Override
  public void deleteByPolicyId(UUID policyId) {
    repository.deleteByPolicyId(policyId);
  }
}
