package com.parentalcontrol.user.dao;

import com.parentalcontrol.user.repository.PersonalDataPurgeRepository;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PersonalDataPurgeDaoImpl implements PersonalDataPurgeDao {

  private final PersonalDataPurgeRepository repository;

  @Override
  public void deleteNotificationLogs(Collection<UUID> deviceIds) {
    repository.deleteNotificationLogs(deviceIds);
  }

  @Override
  public void deleteLocationHistory(Collection<UUID> deviceIds) {
    repository.deleteLocationHistory(deviceIds);
  }

  @Override
  public void deleteAppUsageLogs(Collection<UUID> deviceIds) {
    repository.deleteAppUsageLogs(deviceIds);
  }

  @Override
  public void deleteInstalledApps(Collection<UUID> deviceIds) {
    repository.deleteInstalledApps(deviceIds);
  }

  @Override
  public void deleteAlerts(Collection<UUID> deviceIds) {
    repository.deleteAlerts(deviceIds);
  }

  @Override
  public void deleteAuditLogsForUser(UUID userId) {
    repository.deleteAuditLogsForUser(userId);
  }

  @Override
  public void deleteEmailVerificationTokens(UUID userId) {
    repository.deleteEmailVerificationTokens(userId);
  }

  @Override
  public void deletePairingCodes(UUID parentProfileId) {
    repository.deletePairingCodes(parentProfileId);
  }

  @Override
  public void deleteSubscriptionsAndPayments(UUID parentProfileId) {
    repository.deleteSubscriptionsAndPayments(parentProfileId);
  }
}
