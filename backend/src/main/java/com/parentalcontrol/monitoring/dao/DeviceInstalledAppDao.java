package com.parentalcontrol.monitoring.dao;

import com.parentalcontrol.monitoring.domain.DeviceInstalledApp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for DeviceInstalledApp. Services depend on this interface;
 * DeviceInstalledAppDaoImpl delegates to DeviceInstalledAppRepository.
 */
public interface DeviceInstalledAppDao {

  Optional<DeviceInstalledApp> findById(UUID id);

  DeviceInstalledApp save(DeviceInstalledApp entity);

  <S extends DeviceInstalledApp> List<S> saveAll(Iterable<S> entities);

  void delete(DeviceInstalledApp entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends DeviceInstalledApp> entities);

  boolean existsById(UUID id);

  long count();

  List<DeviceInstalledApp> findAll();

  void flush();

  List<DeviceInstalledApp> findByDeviceIdOrderByAppLabelAsc(UUID deviceId);

  void softDeleteAllByDeviceId(UUID deviceId);

  void deleteAllByDeviceId(UUID deviceId);
}
