package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.AppBlockRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for AppBlockRule. Services depend on this interface; AppBlockRuleDaoImpl
 * delegates to AppBlockRuleRepository.
 */
public interface AppBlockRuleDao {

  Optional<AppBlockRule> findById(UUID id);

  AppBlockRule save(AppBlockRule entity);

  <S extends AppBlockRule> List<S> saveAll(Iterable<S> entities);

  void delete(AppBlockRule entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends AppBlockRule> entities);

  boolean existsById(UUID id);

  long count();

  List<AppBlockRule> findAll();

  void flush();

  List<AppBlockRule> findByPolicyId(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
