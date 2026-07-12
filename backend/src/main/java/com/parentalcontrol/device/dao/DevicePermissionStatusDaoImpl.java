package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.DevicePermissionStatus;
import com.parentalcontrol.device.repository.DevicePermissionStatusRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DevicePermissionStatusDaoImpl implements DevicePermissionStatusDao {

  private final DevicePermissionStatusRepository repository;

  @Override
  public Optional<DevicePermissionStatus> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public DevicePermissionStatus save(DevicePermissionStatus entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends DevicePermissionStatus> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(DevicePermissionStatus entity) {
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
  public void deleteAll(Iterable<? extends DevicePermissionStatus> entities) {
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
  public List<DevicePermissionStatus> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public Optional<DevicePermissionStatus> findByDeviceId(UUID deviceId) {
    return repository.findByDeviceId(deviceId);
  }
}
