package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.DeviceScheduleRule;
import com.parentalcontrol.policy.repository.DeviceScheduleRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceScheduleRuleDaoImpl implements DeviceScheduleRuleDao {

  private final DeviceScheduleRuleRepository repository;

  @Override
  public Optional<DeviceScheduleRule> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public DeviceScheduleRule save(DeviceScheduleRule entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends DeviceScheduleRule> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(DeviceScheduleRule entity) {
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
  public void deleteAll(Iterable<? extends DeviceScheduleRule> entities) {
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
  public List<DeviceScheduleRule> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<DeviceScheduleRule> findByPolicyId(UUID policyId) {
    return repository.findByPolicyId(policyId);
  }

  @Override
  public void deleteByPolicyId(UUID policyId) {
    repository.deleteByPolicyId(policyId);
  }
}
