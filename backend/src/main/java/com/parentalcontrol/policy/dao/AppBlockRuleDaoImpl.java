package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.AppBlockRule;
import com.parentalcontrol.policy.repository.AppBlockRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AppBlockRuleDaoImpl implements AppBlockRuleDao {

  private final AppBlockRuleRepository repository;

  @Override
  public Optional<AppBlockRule> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public AppBlockRule save(AppBlockRule entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends AppBlockRule> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(AppBlockRule entity) {
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
  public void deleteAll(Iterable<? extends AppBlockRule> entities) {
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
  public List<AppBlockRule> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<AppBlockRule> findByPolicyId(UUID policyId) {
    return repository.findByPolicyId(policyId);
  }

  @Override
  public void deleteByPolicyId(UUID policyId) {
    repository.deleteByPolicyId(policyId);
  }
}
