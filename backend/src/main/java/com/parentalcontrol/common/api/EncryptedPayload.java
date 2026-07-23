package com.parentalcontrol.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Airtel-style wire envelope: {@code {"data":"<Base64 AES-CBC ciphertext>"}}. */
public record EncryptedPayload(@JsonProperty("data") String data) {}
