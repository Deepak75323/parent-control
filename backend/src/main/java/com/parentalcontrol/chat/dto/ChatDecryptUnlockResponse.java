package com.parentalcontrol.chat.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatDecryptUnlockResponse {
  /** Base64 PBKDF2 salt for deriving the client AES key. */
  String cryptoSalt;
}
