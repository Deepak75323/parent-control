package com.parentalcontrol.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.common.api.EncryptedPayload;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.AirtelAesPayloadCrypto;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Decrypts Airtel-style request envelopes after HMAC (HMAC signs ciphertext), and encrypts JSON
 * responses. Runs as MVC advice so {@link DeviceSecurityInterceptor} still sees the wire body.
 */
@RestControllerAdvice(basePackages = "com.parentalcontrol")
@RequiredArgsConstructor
public class ApiPayloadEncryptionAdvice extends RequestBodyAdviceAdapter
    implements ResponseBodyAdvice<Object> {

  private static final List<String> EXEMPT_PREFIXES =
      List.of("/actuator", "/v3/api-docs", "/swagger-ui", "/ws");

  private final ApiPayloadEncryptionProperties properties;
  private final AirtelAesPayloadCrypto crypto;
  private final ObjectMapper objectMapper;

  // ---- Request ----

  @Override
  public boolean supports(
      MethodParameter methodParameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return properties.isEnabled()
        && AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
  }

  @Override
  public HttpInputMessage beforeBodyRead(
      HttpInputMessage inputMessage,
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType)
      throws IOException {
    if (!properties.isEnabled()) {
      return inputMessage;
    }
    byte[] body = inputMessage.getBody().readAllBytes();
    if (body.length == 0) {
      return wrapped(inputMessage, body);
    }
    String raw = new String(body, StandardCharsets.UTF_8).trim();
    try {
      JsonNode node = objectMapper.readTree(raw);
      if (!isEncryptedEnvelope(node)) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR,
            "Encrypted API payload required (Airtel-style data envelope)");
      }
      String plaintext = crypto.decrypt(node.get("data").asText());
      return wrapped(inputMessage, plaintext.getBytes(StandardCharsets.UTF_8));
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid encrypted API payload");
    }
  }

  // ---- Response ----

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return properties.isEnabled()
        && AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (!properties.isEnabled() || body == null || body instanceof EncryptedPayload) {
      return body;
    }
    if (isExempt(request)) {
      return body;
    }
    try {
      String json = objectMapper.writeValueAsString(body);
      return new EncryptedPayload(crypto.encrypt(json));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt API response", e);
    }
  }

  /** Airtel wire body is exactly {@code {"data":"<ciphertext>"}} (single field). */
  private static boolean isEncryptedEnvelope(JsonNode node) {
    return node != null
        && node.isObject()
        && node.size() == 1
        && node.has("data")
        && node.get("data").isTextual();
  }

  private boolean isExempt(ServerHttpRequest request) {
    String path = request.getURI().getPath();
    if (request instanceof ServletServerHttpRequest servletRequest) {
      path = servletRequest.getServletRequest().getRequestURI();
    }
    String finalPath = path;
    return EXEMPT_PREFIXES.stream().anyMatch(finalPath::startsWith);
  }

  private static HttpInputMessage wrapped(HttpInputMessage original, byte[] body) {
    return new HttpInputMessage() {
      @Override
      public InputStream getBody() {
        return new ByteArrayInputStream(body);
      }

      @Override
      public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(original.getHeaders());
        headers.setContentLength(body.length);
        return headers;
      }
    };
  }
}
