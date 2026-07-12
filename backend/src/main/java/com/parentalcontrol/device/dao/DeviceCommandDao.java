package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.DeviceCommand;
import com.parentalcontrol.device.domain.DeviceCommandStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access facade for DeviceCommand. Services depend on this interface; DeviceCommandDaoImpl
 * delegates to DeviceCommandRepository.
 */
public interface DeviceCommandDao {

  Optional<DeviceCommand> findById(UUID id);

  DeviceCommand save(DeviceCommand entity);

  <S extends DeviceCommand> List<S> saveAll(Iterable<S> entities);

  void delete(DeviceCommand entity);

  void deleteById(UUID id);

  void deleteAll();

  void deleteAll(Iterable<? extends DeviceCommand> entities);

  boolean existsById(UUID id);

  long count();

  List<DeviceCommand> findAll();

  void flush();

  List<DeviceCommand> findByDeviceIdAndStatusOrderByCreatedAtAsc(
      UUID deviceId, DeviceCommandStatus status);

  int requeueStaleDelivered(UUID deviceId, Instant cutoff, int maxAttempts);

  int failExhaustedStaleDelivered(UUID deviceId, Instant cutoff, int maxAttempts, Instant now);

  int recordExecutionFingerprint(
      UUID commandId, UUID deviceId, Instant now, boolean success, String errorMessage);

  List<DeviceCommand> findByDeviceIdAndStatusAndExecutedAtIsNotNullAndExecutedAtBefore(
      UUID deviceId, DeviceCommandStatus status, Instant executedBefore);

  List<DeviceCommand> findByDeviceIdAndStatusAndExecutedAtIsNullOrderByDeliveredAtAsc(
      UUID deviceId, DeviceCommandStatus status);

  int claimPendingCommand(UUID commandId, UUID deviceId, Instant now, int maxAttempts);

  int completeDeliveredCommand(UUID commandId, UUID deviceId, Instant now);

  int failDeliveredCommand(UUID commandId, UUID deviceId, Instant now, String errorMessage);
}
