package com.parentalcontrol.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.policy.dto.PolicyBundleResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PolicyVersionHasher {

  private final ObjectMapper objectMapper;

  public String hash(PolicyBundleResponse bundle) {
    try {
      byte[] json = objectMapper.writeValueAsBytes(bundle);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(json);
      return HexFormat.of().formatHex(hash).substring(0, 16);
    } catch (JsonProcessingException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("Failed to compute policy version", e);
    }
  }
}
