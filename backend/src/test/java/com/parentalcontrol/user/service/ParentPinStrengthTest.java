package com.parentalcontrol.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parentalcontrol.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class ParentPinStrengthTest {

  @Test
  void acceptsStrongPassword() {
    assertThatCode(() -> ParentPinService.validatePinStrength("Secure99"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsShortOrWeakPasswords() {
    assertThatThrownBy(() -> ParentPinService.validatePinStrength("Ab1"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> ParentPinService.validatePinStrength("abcdefgh"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> ParentPinService.validatePinStrength("12345678"))
        .isInstanceOf(BusinessException.class);
  }
}
