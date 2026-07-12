package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.DeviceInstalledApp;
import com.parentalcontrol.monitoring.repository.DeviceInstalledAppRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceInstalledAppDaoImpl implements DeviceInstalledAppDao {

  private final DeviceInstalledAppRepository repository;

  @Override
  public Optional<DeviceInstalledApp> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public DeviceInstalledApp save(DeviceInstalledApp entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends DeviceInstalledApp> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(DeviceInstalledApp entity) {
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
  public void deleteAll(Iterable<? extends DeviceInstalledApp> entities) {
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
  public List<DeviceInstalledApp> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<DeviceInstalledApp> findByDeviceIdOrderByAppLabelAsc(UUID deviceId) {
    return repository.findByDeviceIdOrderByAppLabelAsc(deviceId);
  }

  @Override
  public void softDeleteAllByDeviceId(UUID deviceId) {
    repository.softDeleteAllByDeviceId(deviceId);
  }

  @Override
  public void deleteAllByDeviceId(UUID deviceId) {
    repository.deleteAllByDeviceId(deviceId);
  }
}
