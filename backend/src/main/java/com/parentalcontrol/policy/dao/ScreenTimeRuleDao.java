package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.ScreenTimeRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for ScreenTimeRule. Services depend on this interface; ScreenTimeRuleDaoImpl
 * delegates to ScreenTimeRuleRepository.
 */
public interface ScreenTimeRuleDao {

  Optional<ScreenTimeRule> findById(UUID id);

  ScreenTimeRule save(ScreenTimeRule entity);

  <S extends ScreenTimeRule> List<S> saveAll(Iterable<S> entities);

  void delete(ScreenTimeRule entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends ScreenTimeRule> entities);

  boolean existsById(UUID id);

  long count();

  List<ScreenTimeRule> findAll();

  void flush();

  List<ScreenTimeRule> findByPolicyId(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
