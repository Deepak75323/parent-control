package com.parentalcontrol.common.constant;

/** Database table names referenced by native queries. */
public final class DatabaseConstants {

  public static final String TABLE_NOTIFICATION_LOGS = "notification_logs";
  public static final String TABLE_LOCATION_HISTORY = "location_history";
  public static final String TABLE_APP_USAGE_LOGS = "app_usage_logs";
  public static final String TABLE_DEVICE_INSTALLED_APPS = "device_installed_apps";
  public static final String TABLE_ALERTS = "alerts";
  public static final String TABLE_AUDIT_LOGS = "audit_logs";
  public static final String TABLE_EMAIL_VERIFICATION_TOKENS = "email_verification_tokens";
  public static final String TABLE_PAIRING_CODES = "pairing_codes";
  public static final String TABLE_PAYMENTS = "payments";
  public static final String TABLE_SUBSCRIPTIONS = "subscriptions";

  public static final String COL_DEVICE_ID = "device_id";
  public static final String COL_USER_ID = "user_id";
  public static final String COL_PARENT_PROFILE_ID = "parent_profile_id";
  public static final String COL_SUBSCRIPTION_ID = "subscription_id";

  private DatabaseConstants() {}
}
