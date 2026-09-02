package com.parentalcontrol.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatNicknameGeneratorTest {

  @Test
  void sameEmailGetsSameNickname() {
    assertEquals(
        ChatNicknameGenerator.fromEmail("anilkumar@gmail.com"),
        ChatNicknameGenerator.fromEmail("anilkumar@gmail.com"));
  }

  @Test
  void emailNormalizationIsCaseInsensitive() {
    assertEquals(
        ChatNicknameGenerator.fromEmail("AnilKumar@gmail.com"),
        ChatNicknameGenerator.fromEmail("  anilkumar@gmail.com  "));
  }

  @Test
  void differentEmailsUsuallyGetDifferentNicknames() {
    String first = ChatNicknameGenerator.fromEmail("parent-a@local.test");
    String second = ChatNicknameGenerator.fromEmail("parent-b@local.test");
    assertNotEquals(first, second);
  }

  @Test
  void nicknameLooksLikeTwoCapitalizedWords() {
    String nickname = ChatNicknameGenerator.fromEmail("chat-a@local.test");
    assertTrue(nickname.matches("^[A-Z][a-z]+ [A-Z][a-z]+$"));
  }
}
