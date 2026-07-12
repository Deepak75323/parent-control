package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.PolicyAssignment;
import com.parentalcontrol.policy.repository.PolicyAssignmentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PolicyAssignmentDaoImpl implements PolicyAssignmentDao {

  private final PolicyAssignmentRepository repository;

  @Override
  public Optional<PolicyAssignment> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public PolicyAssignment save(PolicyAssignment entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends PolicyAssignment> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(PolicyAssignment entity) {
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
  public void deleteAll(Iterable<? extends PolicyAssignment> entities) {
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
  public List<PolicyAssignment> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<PolicyAssignment> findByDeviceIdAndActiveTrue(UUID deviceId) {
    return repository.findByDeviceIdAndActiveTrue(deviceId);
  }

  @Override
  public List<PolicyAssignment> findByPolicyId(UUID policyId) {
    return repository.findByPolicyId(policyId);
  }

  @Override
  public List<UUID> findActivePolicyIdsByDeviceId(UUID deviceId) {
    return repository.findActivePolicyIdsByDeviceId(deviceId);
  }
}
