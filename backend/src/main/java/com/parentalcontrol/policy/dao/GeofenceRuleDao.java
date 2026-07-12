package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.GeofenceRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for GeofenceRule. Services depend on this interface; GeofenceRuleDaoImpl
 * delegates to GeofenceRuleRepository.
 */
public interface GeofenceRuleDao {

  Optional<GeofenceRule> findById(UUID id);

  GeofenceRule save(GeofenceRule entity);

  <S extends GeofenceRule> List<S> saveAll(Iterable<S> entities);

  void delete(GeofenceRule entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends GeofenceRule> entities);

  boolean existsById(UUID id);

  long count();

  List<GeofenceRule> findAll();

  void flush();

  List<GeofenceRule> findByPolicyId(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
