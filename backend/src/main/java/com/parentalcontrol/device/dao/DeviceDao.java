package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for Device. Services depend on this interface; DeviceDaoImpl delegates to
 * DeviceRepository.
 */
public interface DeviceDao {

  Optional<Device> findById(UUID id);

  Device save(Device entity);

  <S extends Device> List<S> saveAll(Iterable<S> entities);

  void delete(Device entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends Device> entities);

  boolean existsById(UUID id);

  long count();

  List<Device> findAll();

  void flush();

  List<Device> findByParentProfileIdOrderByCreatedAtDesc(UUID parentProfileId);

  List<Device> findActiveByParentProfileIdOrderByCreatedAtDesc(UUID parentProfileId);

  List<Device> findAllActive();

  Optional<Device> findByAndroidId(String androidId);

  long countByParentProfileId(UUID parentProfileId);

  List<Device> findByStatusAndLastSeenAtBefore(DeviceStatus status, Instant cutoff);
}
