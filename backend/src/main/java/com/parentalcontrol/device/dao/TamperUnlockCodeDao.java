package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.TamperUnlockCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for TamperUnlockCode. Services depend on this interface;
 * TamperUnlockCodeDaoImpl delegates to TamperUnlockCodeRepository.
 */
public interface TamperUnlockCodeDao {

  Optional<TamperUnlockCode> findById(UUID id);

  TamperUnlockCode save(TamperUnlockCode entity);

  <S extends TamperUnlockCode> List<S> saveAll(Iterable<S> entities);

  void delete(TamperUnlockCode entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends TamperUnlockCode> entities);

  boolean existsById(UUID id);

  long count();

  List<TamperUnlockCode> findAll();

  void flush();

  Optional<TamperUnlockCode>
      findFirstByDeviceIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
          UUID deviceId, Instant now);

  void invalidateActiveForDevice(UUID deviceId, Instant now);
}
