package com.parentalcontrol.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.api.EncryptedPayload;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.AirtelAesPayloadCrypto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Generic JSON error bodies for Spring Security — no stack traces or internal details. */
@Component
@RequiredArgsConstructor
public class RestSecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper;
  private final ApiPayloadEncryptionProperties apiPayloadEncryptionProperties;
  private final AirtelAesPayloadCrypto airtelAesPayloadCrypto;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    writeError(response, ErrorCode.UNAUTHORIZED, "Authentication required");
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    writeError(response, ErrorCode.FORBIDDEN, "Access denied");
  }

  private void writeError(HttpServletResponse response, ErrorCode code, String message)
      throws IOException {
    response.setStatus(code.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ApiResponse<Void> body = ApiResponse.error(code, message);
    if (apiPayloadEncryptionProperties.isEnabled()) {
      String json = objectMapper.writeValueAsString(body);
      objectMapper.writeValue(
          response.getOutputStream(), new EncryptedPayload(airtelAesPayloadCrypto.encrypt(json)));
    } else {
      objectMapper.writeValue(response.getOutputStream(), body);
    }
  }
}
