package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.PairingCode;
import com.parentalcontrol.device.repository.PairingCodeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PairingCodeDaoImpl implements PairingCodeDao {

  private final PairingCodeRepository repository;

  @Override
  public Optional<PairingCode> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public PairingCode save(PairingCode entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends PairingCode> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(PairingCode entity) {
    repository.delete(entity);
  }

  @Override
  public void deleteById(UUID id) {
    repository.deleteById(id);
  }

  @Override
  public void deleteAll() {
    repository.deleteAll();
  }

  @Override
  public void deleteAll(Iterable<? extends PairingCode> entities) {
    repository.deleteAll(entities);
  }

  @Override
  public boolean existsById(UUID id) {
    return repository.existsById(id);
  }

  @Override
  public long count() {
    return repository.count();
  }

  @Override
  public List<PairingCode> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<PairingCode> findByCodeIgnoreCase(String code) {
    return repository.findByCodeIgnoreCase(code);
  }

  @Override
  public boolean existsByCodeIgnoreCase(String code) {
    return repository.existsByCodeIgnoreCase(code);
  }

  @Override
  public int claimCode(UUID id, UUID deviceId, Instant usedAt, Instant now) {
    return repository.claimCode(id, deviceId, usedAt, now);
  }

  @Override
  public java.util.List<PairingCode> findActiveUnusedWithPassword(Instant now) {
    return repository.findActiveUnusedWithPassword(now);
  }

  @Override
  public int expireActiveUnusedForParent(UUID parentProfileId, Instant now) {
    return repository.expireActiveUnusedForParent(parentProfileId, now);
  }
}
