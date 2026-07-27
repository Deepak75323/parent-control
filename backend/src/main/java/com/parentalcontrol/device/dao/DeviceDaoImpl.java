package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.repository.DeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceDaoImpl implements DeviceDao {

  private final DeviceRepository repository;

  @Override
  public Optional<Device> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public Device save(Device entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends Device> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(Device entity) {
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
  public void deleteAll(Iterable<? extends Device> entities) {
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
  public List<Device> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<Device> findByParentProfileIdOrderByCreatedAtDesc(UUID parentProfileId) {
    return repository.findByParentProfileIdOrderByCreatedAtDesc(parentProfileId);
  }

  @Override
  public List<Device> findActiveByParentProfileIdOrderByCreatedAtDesc(UUID parentProfileId) {
    return repository.findActiveByParentProfileIdOrderByCreatedAtDesc(parentProfileId);
  }

  @Override
  public List<Device> findAllActive() {
    return repository.findAllActive();
  }

  @Override
  public Optional<Device> findByAndroidId(String androidId) {
    return repository.findByAndroidId(androidId);
  }

  @Override
  public long countByParentProfileId(UUID parentProfileId) {
    return repository.countByParentProfileId(parentProfileId);
  }

  @Override
  public long countNonRevokedByParentProfileId(UUID parentProfileId) {
    return repository.countNonRevokedByParentProfileId(parentProfileId, DeviceStatus.REVOKED);
  }

  @Override
  public List<Device> findByStatusAndLastSeenAtBefore(DeviceStatus status, Instant cutoff) {
    return repository.findByStatusAndLastSeenAtBefore(status, cutoff);
  }
}
