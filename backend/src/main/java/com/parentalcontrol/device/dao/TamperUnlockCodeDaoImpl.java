package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.TamperUnlockCode;
import com.parentalcontrol.device.repository.TamperUnlockCodeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TamperUnlockCodeDaoImpl implements TamperUnlockCodeDao {

  private final TamperUnlockCodeRepository repository;

  @Override
  public Optional<TamperUnlockCode> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public TamperUnlockCode save(TamperUnlockCode entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends TamperUnlockCode> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(TamperUnlockCode entity) {
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
  public void deleteAll(Iterable<? extends TamperUnlockCode> entities) {
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
  public List<TamperUnlockCode> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<TamperUnlockCode>
      findFirstByDeviceIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
          UUID deviceId, Instant now) {
    return repository.findFirstByDeviceIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
        deviceId, now);
  }

  @Override
  public void invalidateActiveForDevice(UUID deviceId, Instant now) {
    repository.invalidateActiveForDevice(deviceId, now);
  }
}
