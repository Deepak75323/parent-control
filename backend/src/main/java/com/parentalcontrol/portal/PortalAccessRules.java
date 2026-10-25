package com.parentalcontrol.portal;

/** Pure rules for who may disable/enable the parent website. */
public final class PortalAccessRules {

  public static final String BOOTSTRAP_ADMIN_EMAIL = "anilkumar@gmail.com";

  private PortalAccessRules() {}

  public static boolean isBootstrapAdmin(String email) {
    return email != null && BOOTSTRAP_ADMIN_EMAIL.equalsIgnoreCase(email.trim());
  }

  /**
   * Bootstrap admin always controls the portal. When {@code sharedWithEveryone} is true, any
   * authenticated parent may also toggle disable/enable.
   */
  public static boolean canControl(String email, boolean sharedWithEveryone) {
    return isBootstrapAdmin(email) || sharedWithEveryone;
  }

  /** Only the bootstrap admin may share (or revoke) control with everyone. */
  public static boolean canShare(String email) {
    return isBootstrapAdmin(email);
  }
}
