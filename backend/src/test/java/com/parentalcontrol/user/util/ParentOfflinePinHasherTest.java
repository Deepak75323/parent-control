package com.parentalcontrol.user.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParentOfflinePinHasherTest {

  @Test
  void createAndMatch_roundTrips() {
    var material = ParentOfflinePinHasher.create("SecretPass1");
    assertTrue(
        ParentOfflinePinHasher.matches(
            "SecretPass1",
            material.saltBase64(),
            material.hashBase64(),
            material.iterations()));
    assertFalse(
        ParentOfflinePinHasher.matches(
            "wrong",
            material.saltBase64(),
            material.hashBase64(),
            material.iterations()));
  }
}
