package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.PolicyAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for PolicyAssignment. Services depend on this interface;
 * PolicyAssignmentDaoImpl delegates to PolicyAssignmentRepository.
 */
public interface PolicyAssignmentDao {

  Optional<PolicyAssignment> findById(UUID id);

  PolicyAssignment save(PolicyAssignment entity);

  <S extends PolicyAssignment> List<S> saveAll(Iterable<S> entities);

  void delete(PolicyAssignment entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends PolicyAssignment> entities);

  boolean existsById(UUID id);

  long count();

  List<PolicyAssignment> findAll();

  void flush();

  List<PolicyAssignment> findByDeviceIdAndActiveTrue(UUID deviceId);

  List<PolicyAssignment> findByPolicyId(UUID policyId);

  List<UUID> findActivePolicyIdsByDeviceId(UUID deviceId);
}
