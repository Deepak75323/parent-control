package com.parentalcontrol.user.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FamilyHandleNormalizerTest {

  @Test
  void stripsAtAndLowercases() {
    assertThat(FamilyHandleNormalizer.normalize("@Mom.Sharma")).isEqualTo("mom.sharma");
    assertThat(FamilyHandleNormalizer.normalize("  FAM-MOM7 ")).isEqualTo("fam-mom7");
  }

  @Test
  void acceptsValidHandles() {
    assertThat(FamilyHandleNormalizer.isValidFormat("mom.sharma")).isTrue();
    assertThat(FamilyHandleNormalizer.isValidFormat("fam-mom7")).isTrue();
    assertThat(FamilyHandleNormalizer.isValidFormat("ab1")).isTrue();
  }

  @Test
  void rejectsInvalidHandles() {
    assertThat(FamilyHandleNormalizer.isValidFormat("ab")).isFalse();
    assertThat(FamilyHandleNormalizer.isValidFormat("-bad")).isFalse();
    assertThat(FamilyHandleNormalizer.isValidFormat("bad-")).isFalse();
    assertThat(FamilyHandleNormalizer.isValidFormat("has space")).isFalse();
  }

  @Test
  void rejectsReserved() {
    assertThat(FamilyHandleNormalizer.isReserved("admin")).isTrue();
    assertThat(FamilyHandleNormalizer.isReserved("mom.sharma")).isFalse();
  }
}
