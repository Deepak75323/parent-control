package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.ScreenTimeRule;
import com.parentalcontrol.policy.repository.ScreenTimeRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScreenTimeRuleDaoImpl implements ScreenTimeRuleDao {

  private final ScreenTimeRuleRepository repository;

  @Override
  public Optional<ScreenTimeRule> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public ScreenTimeRule save(ScreenTimeRule entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends ScreenTimeRule> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(ScreenTimeRule entity) {
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
  public void deleteAll(Iterable<? extends ScreenTimeRule> entities) {
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
  public List<ScreenTimeRule> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<ScreenTimeRule> findByPolicyId(UUID policyId) {
    return repository.findByPolicyId(policyId);
  }

  @Override
  public void deleteByPolicyId(UUID policyId) {
    repository.deleteByPolicyId(policyId);
  }
}
