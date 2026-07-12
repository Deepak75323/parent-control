package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.DeviceProvisioningEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for DeviceProvisioningEvent. Services depend on this interface;
 * DeviceProvisioningEventDaoImpl delegates to DeviceProvisioningEventRepository.
 */
public interface DeviceProvisioningEventDao {

  Optional<DeviceProvisioningEvent> findById(UUID id);

  DeviceProvisioningEvent save(DeviceProvisioningEvent entity);

  <S extends DeviceProvisioningEvent> List<S> saveAll(Iterable<S> entities);

  void delete(DeviceProvisioningEvent entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends DeviceProvisioningEvent> entities);

  boolean existsById(UUID id);

  long count();

  List<DeviceProvisioningEvent> findAll();

  void flush();
}
