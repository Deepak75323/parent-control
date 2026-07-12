package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.PairingCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for PairingCode. Services depend on this interface; PairingCodeDaoImpl
 * delegates to PairingCodeRepository.
 */
public interface PairingCodeDao {

  Optional<PairingCode> findById(UUID id);

  PairingCode save(PairingCode entity);

  <S extends PairingCode> List<S> saveAll(Iterable<S> entities);

  void delete(PairingCode entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends PairingCode> entities);

  boolean existsById(UUID id);

  long count();

  List<PairingCode> findAll();

  void flush();

  Optional<PairingCode> findByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCase(String code);

  int claimCode(UUID id, UUID deviceId, Instant usedAt, Instant now);

  java.util.List<PairingCode> findActiveUnusedWithPassword(Instant now);

  int expireActiveUnusedForParent(UUID parentProfileId, Instant now);
}
