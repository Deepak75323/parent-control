package com.parentalcontrol.portal;

import java.util.Collection;
import java.util.UUID;

/** Pure rules for who may disable/enable the parent website. */
public final class PortalAccessRules {

  public static final String BOOTSTRAP_ADMIN_EMAIL = "anilkumar@gmail.com";

  private PortalAccessRules() {}

  public static boolean isBootstrapAdmin(String email) {
    return email != null && BOOTSTRAP_ADMIN_EMAIL.equalsIgnoreCase(email.trim());
  }

  /**
   * Bootstrap admin always controls. Parents granted via family handle (operator user ids) may
   * also toggle disable/enable.
   */
  public static boolean canControl(String email, UUID userId, Collection<UUID> operatorUserIds) {
    if (isBootstrapAdmin(email)) {
      return true;
    }
    return userId != null && operatorUserIds != null && operatorUserIds.contains(userId);
  }

  /** Only the bootstrap admin may grant or revoke control by family handle. */
  public static boolean canShare(String email) {
    return isBootstrapAdmin(email);
  }
}
