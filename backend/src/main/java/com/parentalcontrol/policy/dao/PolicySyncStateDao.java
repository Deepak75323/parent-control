package com.parentalcontrol.policy.dao;

import com.parentalcontrol.policy.domain.PolicySyncState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for PolicySyncState. Services depend on this interface; PolicySyncStateDaoImpl
 * delegates to PolicySyncStateRepository.
 */
public interface PolicySyncStateDao {

  Optional<PolicySyncState> findById(UUID id);

  PolicySyncState save(PolicySyncState entity);

  <S extends PolicySyncState> List<S> saveAll(Iterable<S> entities);

  void delete(PolicySyncState entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends PolicySyncState> entities);

  boolean existsById(UUID id);

  long count();

  List<PolicySyncState> findAll();

  void flush();

  Optional<PolicySyncState> findByDeviceId(UUID deviceId);
}
