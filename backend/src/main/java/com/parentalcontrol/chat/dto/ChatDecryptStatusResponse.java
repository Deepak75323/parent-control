package com.parentalcontrol.chat.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatDecryptStatusResponse {
  boolean configured;
  /** Base64 PBKDF2 salt when configured; null otherwise. */
  String cryptoSalt;
}
