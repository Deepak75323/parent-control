package com.parentalcontrol.portal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortalAccessRulesTest {

  private static final UUID OPERATOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Test
  void bootstrapAdminAlwaysControlsAndShares() {
    assertTrue(PortalAccessRules.isBootstrapAdmin("anilkumar@gmail.com"));
    assertTrue(PortalAccessRules.isBootstrapAdmin("AnilKumar@Gmail.com"));
    assertTrue(PortalAccessRules.canControl("anilkumar@gmail.com", OTHER, Set.of()));
    assertTrue(PortalAccessRules.canShare("anilkumar@gmail.com"));
  }

  @Test
  void operatorCanControlButNotShare() {
    assertTrue(PortalAccessRules.canControl("other@example.com", OPERATOR, Set.of(OPERATOR)));
    assertFalse(PortalAccessRules.canShare("other@example.com"));
  }

  @Test
  void nonOperatorCannotControl() {
    assertFalse(PortalAccessRules.canControl("other@example.com", OTHER, Set.of(OPERATOR)));
    assertFalse(PortalAccessRules.canControl("other@example.com", OTHER, Set.of()));
    assertFalse(PortalAccessRules.canControl(null, null, Set.of(OPERATOR)));
  }
}
