package com.parentalcontrol.user.dao;

import java.util.Collection;
import java.util.UUID;

/** Data-access facade for hard-deleting monitoring PII during account deletion. */
public interface PersonalDataPurgeDao {

  void deleteNotificationLogs(Collection<UUID> deviceIds);

  void deleteLocationHistory(Collection<UUID> deviceIds);

  void deleteAppUsageLogs(Collection<UUID> deviceIds);

  void deleteInstalledApps(Collection<UUID> deviceIds);

  void deleteAlerts(Collection<UUID> deviceIds);

  void deleteAuditLogsForUser(UUID userId);

  void deleteEmailVerificationTokens(UUID userId);

  void deletePairingCodes(UUID parentProfileId);

  void deleteSubscriptionsAndPayments(UUID parentProfileId);
}
