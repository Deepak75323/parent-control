package com.parentalcontrol.device.dao;

import com.parentalcontrol.device.domain.DeviceCommand;
import com.parentalcontrol.device.domain.DeviceCommandStatus;
import com.parentalcontrol.device.repository.DeviceCommandRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceCommandDaoImpl implements DeviceCommandDao {

  private final DeviceCommandRepository repository;

  @Override
  public Optional<DeviceCommand> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public DeviceCommand save(DeviceCommand entity) {
    return repository.save(entity);
  }

  @Override
  public <S extends DeviceCommand> List<S> saveAll(Iterable<S> entities) {
    return repository.saveAll(entities);
  }

  @Override
  public void delete(DeviceCommand entity) {
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
  public void deleteAll(Iterable<? extends DeviceCommand> entities) {
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
  public List<DeviceCommand> findAll() {
    return repository.findAll();
  }

  @Override
  public void flush() {
    repository.flush();
  }

  @Override
  public List<DeviceCommand> findByDeviceIdAndStatusOrderByCreatedAtAsc(
      UUID deviceId, DeviceCommandStatus status) {
    return repository.findByDeviceIdAndStatusOrderByCreatedAtAsc(deviceId, status);
  }

  @Override
  public int requeueStaleDelivered(UUID deviceId, Instant cutoff, int maxAttempts) {
    return repository.requeueStaleDelivered(deviceId, cutoff, maxAttempts);
  }

  @Override
  public int failExhaustedStaleDelivered(
      UUID deviceId, Instant cutoff, int maxAttempts, Instant now) {
    return repository.failExhaustedStaleDelivered(deviceId, cutoff, maxAttempts, now);
  }

  @Override
  public int recordExecutionFingerprint(
      UUID commandId, UUID deviceId, Instant now, boolean success, String errorMessage) {
    return repository.recordExecutionFingerprint(commandId, deviceId, now, success, errorMessage);
  }

  @Override
  public List<DeviceCommand> findByDeviceIdAndStatusAndExecutedAtIsNotNullAndExecutedAtBefore(
      UUID deviceId, DeviceCommandStatus status, Instant executedBefore) {
    return repository.findByDeviceIdAndStatusAndExecutedAtIsNotNullAndExecutedAtBefore(
        deviceId, status, executedBefore);
  }

  @Override
  public List<DeviceCommand> findByDeviceIdAndStatusAndExecutedAtIsNullOrderByDeliveredAtAsc(
      UUID deviceId, DeviceCommandStatus status) {
    return repository.findByDeviceIdAndStatusAndExecutedAtIsNullOrderByDeliveredAtAsc(
        deviceId, status);
  }

  @Override
  public int claimPendingCommand(UUID commandId, UUID deviceId, Instant now, int maxAttempts) {
    return repository.claimPendingCommand(commandId, deviceId, now, maxAttempts);
  }

  @Override
  public int completeDeliveredCommand(UUID commandId, UUID deviceId, Instant now) {
    return repository.completeDeliveredCommand(commandId, deviceId, now);
  }

  @Override
  public int failDeliveredCommand(UUID commandId, UUID deviceId, Instant now, String errorMessage) {
    return repository.failDeliveredCommand(commandId, deviceId, now, errorMessage);
  }
}
