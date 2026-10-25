package com.parentalcontrol.portal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortalAccessRulesTest {

  @Test
  void bootstrapAdminAlwaysControls() {
    assertTrue(PortalAccessRules.isBootstrapAdmin("anilkumar@gmail.com"));
    assertTrue(PortalAccessRules.isBootstrapAdmin("AnilKumar@Gmail.com"));
    assertTrue(PortalAccessRules.canControl("anilkumar@gmail.com", false));
    assertTrue(PortalAccessRules.canShare("anilkumar@gmail.com"));
  }

  @Test
  void otherParentsControlOnlyWhenShared() {
    assertFalse(PortalAccessRules.canControl("other@example.com", false));
    assertTrue(PortalAccessRules.canControl("other@example.com", true));
    assertFalse(PortalAccessRules.canShare("other@example.com"));
  }
}
