package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.DeviceScheduleRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for DeviceScheduleRule. Services depend on this interface;
 * DeviceScheduleRuleDaoImpl delegates to DeviceScheduleRuleRepository.
 */
public interface DeviceScheduleRuleDao {

  Optional<DeviceScheduleRule> findById(UUID id);

  DeviceScheduleRule save(DeviceScheduleRule entity);

  <S extends DeviceScheduleRule> List<S> saveAll(Iterable<S> entities);

  void delete(DeviceScheduleRule entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends DeviceScheduleRule> entities);

  boolean existsById(UUID id);

  long count();

  List<DeviceScheduleRule> findAll();

  void flush();

  List<DeviceScheduleRule> findByPolicyId(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
