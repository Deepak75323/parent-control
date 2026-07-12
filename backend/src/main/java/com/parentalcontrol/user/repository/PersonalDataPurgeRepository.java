package com.parentalcontrol.user.repository;

import com.parentalcontrol.common.constant.DatabaseConstants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Hard-deletes monitoring PII when a parent requests account deletion. */
@Repository
public class PersonalDataPurgeRepository {

  @PersistenceContext private EntityManager entityManager;

  @Transactional
  public void deleteNotificationLogs(Collection<UUID> deviceIds) {
    if (deviceIds.isEmpty()) return;
    entityManager
        .createNativeQuery(
            "DELETE FROM "
                + DatabaseConstants.TABLE_NOTIFICATION_LOGS
                + " WHERE "
                + DatabaseConstants.COL_DEVICE_ID
                + " IN (:ids)")
        .setParameter("ids", deviceIds)
        .executeUpdate();
  }

  @Transactional
  public void deleteLocationHistory(Collection<UUID> deviceIds) {
    if (deviceIds.isEmpty()) return;
    entityManager
        .createNativeQuery(
            "DELETE FROM "
                + DatabaseConstants.TABLE_LOCATION_HISTORY
                + " WHERE "
                + DatabaseConstants.COL_DEVICE_ID
                + " IN (:ids)")
        .setParameter("ids", deviceIds)
        .executeUpdate();
  }

  @Transactional
  public void deleteAppUsageLogs(Collection<UUID> deviceIds) {
    if (deviceIds.isEmpty()) return;
    entityManager
        .createNativeQuery(
            "DELETE FROM "
                + DatabaseConstants.TABLE_APP_USAGE_LOGS
                + " WHERE "
                + DatabaseConstants.COL_DEVICE_ID
                + " IN (:ids)")
        .setParameter("ids", deviceIds)
        .executeUpdate();
  }

  @Transactional
  public void deleteInstalledApps(Collection<UUID> deviceIds) {
    if (deviceIds.isEmpty()) return;
    entityManager
        .createNativeQuery(
            "DELETE FROM "
                + DatabaseConstants.TABLE_DEVICE_INSTALLED_APPS
                + " WHERE "
                + DatabaseConstants.COL_DEVICE_ID
                + " IN (:ids)")
        .setParameter("ids", deviceIds)
        .executeUpdate();
  }

  @Transactional
  public void deleteAlerts(Collection<UUID> deviceIds) {
    if (deviceIds.isEmpty()) return;
    entityManager
        .createNativeQuery(
            "DELETE FROM "
                + DatabaseConstants.TABLE_ALERTS
                + " WHERE "
                + DatabaseConstants.COL_DEVICE_ID
                + " IN (:ids)")
        .setParameter("ids", deviceIds)
        .executeUpdate();
  }

  @Transactional
  public void deleteAuditLogsForUser(UUID userId) {
    entityManager
        .createNativeQuery(
            "DELETE FROM "
                + DatabaseConstants.TABLE_AUDIT_LOGS
                + " WHERE "
                + DatabaseConstants.COL_USER_ID
                + " = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }

  @Transactional
  public void deleteEmailVerificationTokens(UUID userId) {
    entityManager
        .createNativeQuery(
            "DELETE FROM "
                + DatabaseConstants.TABLE_EMAIL_VERIFICATION_TOKENS
                + " WHERE "
                + DatabaseConstants.COL_USER_ID
                + " = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }

  @Transactional
  public void deletePairingCodes(UUID parentProfileId) {
    entityManager
        .createNativeQuery(
            "DELETE FROM "
                + DatabaseConstants.TABLE_PAIRING_CODES
                + " WHERE "
                + DatabaseConstants.COL_PARENT_PROFILE_ID
                + " = :parentProfileId")
        .setParameter("parentProfileId", parentProfileId)
        .executeUpdate();
  }

  @Transactional
  public void deleteSubscriptionsAndPayments(UUID parentProfileId) {
    entityManager
        .createNativeQuery(
            "DELETE FROM "
                + DatabaseConstants.TABLE_PAYMENTS
                + " WHERE "
                + DatabaseConstants.COL_SUBSCRIPTION_ID
                + " IN ("
                + "SELECT id FROM "
                + DatabaseConstants.TABLE_SUBSCRIPTIONS
                + " WHERE "
                + DatabaseConstants.COL_PARENT_PROFILE_ID
                + " = :parentProfileId)")
        .setParameter("parentProfileId", parentProfileId)
        .executeUpdate();
    entityManager
        .createNativeQuery(
            "DELETE FROM "
                + DatabaseConstants.TABLE_SUBSCRIPTIONS
                + " WHERE "
                + DatabaseConstants.COL_PARENT_PROFILE_ID
                + " = :parentProfileId")
        .setParameter("parentProfileId", parentProfileId)
        .executeUpdate();
  }
}
