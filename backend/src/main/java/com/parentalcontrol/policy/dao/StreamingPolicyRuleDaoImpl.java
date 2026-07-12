package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.StreamingPolicyRule;
import com.parentalcontrol.policy.repository.StreamingPolicyRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StreamingPolicyRuleDaoImpl implements StreamingPolicyRuleDao {

  private final StreamingPolicyRuleRepository repository;

  @Override
  public Optional<StreamingPolicyRule> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public StreamingPolicyRule save(StreamingPolicyRule entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends StreamingPolicyRule> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(StreamingPolicyRule entity) {
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
  public void deleteAll(Iterable<? extends StreamingPolicyRule> entities) {
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
  public List<StreamingPolicyRule> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<StreamingPolicyRule> findByPolicyId(UUID policyId) {
    return repository.findByPolicyId(policyId);
  }

  @Override
  public void deleteByPolicyId(UUID policyId) {
    repository.deleteByPolicyId(policyId);
  }
}
