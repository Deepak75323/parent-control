package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.DevicePermissionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for DevicePermissionStatus. Services depend on this interface;
 * DevicePermissionStatusDaoImpl delegates to DevicePermissionStatusRepository.
 */
public interface DevicePermissionStatusDao {

  Optional<DevicePermissionStatus> findById(UUID id);

  DevicePermissionStatus save(DevicePermissionStatus entity);

  <S extends DevicePermissionStatus> List<S> saveAll(Iterable<S> entities);

  void delete(DevicePermissionStatus entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends DevicePermissionStatus> entities);

  boolean existsById(UUID id);

  long count();

  List<DevicePermissionStatus> findAll();

  void flush();

  Optional<DevicePermissionStatus> findByDeviceId(UUID deviceId);
}
