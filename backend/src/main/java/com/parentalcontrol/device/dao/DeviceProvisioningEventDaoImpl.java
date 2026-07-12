package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.DeviceProvisioningEvent;
import com.parentalcontrol.device.repository.DeviceProvisioningEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceProvisioningEventDaoImpl implements DeviceProvisioningEventDao {

  private final DeviceProvisioningEventRepository repository;

  @Override
  public Optional<DeviceProvisioningEvent> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public DeviceProvisioningEvent save(DeviceProvisioningEvent entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends DeviceProvisioningEvent> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(DeviceProvisioningEvent entity) {
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
  public void deleteAll(Iterable<? extends DeviceProvisioningEvent> entities) {
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
  public List<DeviceProvisioningEvent> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }
}
