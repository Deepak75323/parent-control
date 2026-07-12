package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.PolicyViolationEvent;
import com.parentalcontrol.policy.repository.PolicyViolationEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PolicyViolationEventDaoImpl implements PolicyViolationEventDao {

  private final PolicyViolationEventRepository repository;

  @Override
  public Optional<PolicyViolationEvent> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public PolicyViolationEvent save(PolicyViolationEvent entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends PolicyViolationEvent> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(PolicyViolationEvent entity) {
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
  public void deleteAll(Iterable<? extends PolicyViolationEvent> entities) {
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
  public List<PolicyViolationEvent> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }
}
