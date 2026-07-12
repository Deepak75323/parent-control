package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.Policy;
import com.parentalcontrol.policy.domain.PolicyType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for Policy. Services depend on this interface; PolicyDaoImpl delegates to
 * PolicyRepository.
 */
public interface PolicyDao {

  Optional<Policy> findById(UUID id);

  Policy save(Policy entity);

  <S extends Policy> List<S> saveAll(Iterable<S> entities);

  void delete(Policy entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends Policy> entities);

  boolean existsById(UUID id);

  long count();

  List<Policy> findAll();

  void flush();

  List<Policy> findByParentProfileIdOrderByUpdatedAtDesc(UUID parentProfileId);

  List<Policy> findByParentProfileIdAndPolicyTypeOrderByUpdatedAtDesc(
      UUID parentProfileId, PolicyType policyType);

  List<Policy> findAllById(Iterable<UUID> ids);
}
