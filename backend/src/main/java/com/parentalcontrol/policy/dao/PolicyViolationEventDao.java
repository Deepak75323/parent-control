package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.PolicyViolationEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for PolicyViolationEvent. Services depend on this interface;
 * PolicyViolationEventDaoImpl delegates to PolicyViolationEventRepository.
 */
public interface PolicyViolationEventDao {

  Optional<PolicyViolationEvent> findById(UUID id);

  PolicyViolationEvent save(PolicyViolationEvent entity);

  <S extends PolicyViolationEvent> List<S> saveAll(Iterable<S> entities);

  void delete(PolicyViolationEvent entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends PolicyViolationEvent> entities);

  boolean existsById(UUID id);

  long count();

  List<PolicyViolationEvent> findAll();

  void flush();
}
