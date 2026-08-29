package com.parentalcontrol.config;

import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.common.constant.HeaderConstants;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.security.AuthenticatedUser;
import com.parentalcontrol.common.security.RateLimitService;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.security.DeviceHmacService;
import com.parentalcontrol.observability.SecurityObservabilityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Component
@RequiredArgsConstructor
public class DeviceSecurityInterceptor implements HandlerInterceptor {

  private static final Set<String> HMAC_EXEMPT = Set.of(ApiConstants.DEVICE_PAIR);

  private static final Set<String> HMAC_PATH_PREFIXES =
      Set.of(
          ApiConstants.DEVICE_PREFIX,
          ApiConstants.CHILD_PREFIX,
          ApiConstants.EVENTS_PREFIX,
          ApiConstants.MONITORING_SIGNALING_PREFIX);

  private final DeviceHmacService deviceHmacService;
  private final RateLimitService rateLimitService;
  private final SecurityObservabilityService securityObservabilityService;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String path = request.getRequestURI();
    if (!requiresHmac(path)) {
      return true;
    }

    if ("POST".equalsIgnoreCase(request.getMethod()) && !HMAC_EXEMPT.contains(path)) {
      AuthenticatedUser user = SecurityUtils.currentUser().orElse(null);
      if (user != null && user.getRole() == Role.ROLE_CHILD_DEVICE && user.getDeviceId() != null) {
        UUID deviceId = user.getDeviceId();
        if (ApiConstants.DEVICE_TAMPER_VERIFY.equals(path)) {
          rateLimitService.checkTamperVerify(deviceId.toString());
        } else if (!ApiConstants.DEVICE_TAMPER_ATTEMPT.equals(path)) {
          rateLimitService.checkDeviceIngest(deviceId.toString());
        }
        verifyHmac(request, deviceId, path);
      }
    }
    return true;
  }

  private static boolean requiresHmac(String path) {
    return HMAC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
  }

  private void verifyHmac(HttpServletRequest request, UUID deviceId, String path) {
    String body = readBody(request);
    try {
      deviceHmacService.verify(
          deviceId,
          request.getMethod(),
          path,
          request.getHeader(HeaderConstants.DEVICE_TIMESTAMP),
          request.getHeader(HeaderConstants.DEVICE_NONCE),
          body,
          request.getHeader(HeaderConstants.DEVICE_SIGNATURE));
    } catch (com.parentalcontrol.common.exception.BusinessException ex) {
      if (ex.getErrorCode() == com.parentalcontrol.common.exception.ErrorCode.UNAUTHORIZED
          || ex.getErrorCode() == com.parentalcontrol.common.exception.ErrorCode.FORBIDDEN) {
        securityObservabilityService.recordHmacFailure(ex.getErrorCode().getCode());
      }
      throw ex;
    }
  }

  private static String readBody(HttpServletRequest request) {
    RepeatableBodyRequestWrapper repeatable = findRepeatableWrapper(request);
    if (repeatable != null) {
      return repeatable.bodyAsString();
    }
    if (request instanceof ContentCachingRequestWrapper wrapper) {
      try {
        if (wrapper.getContentAsByteArray().length == 0) {
          wrapper.getInputStream().readAllBytes();
        }
      } catch (Exception ignored) {
        return "";
      }
      return new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
    }
    return "";
  }

  private static RepeatableBodyRequestWrapper findRepeatableWrapper(HttpServletRequest request) {
    HttpServletRequest current = request;
    while (current != null) {
      if (current instanceof RepeatableBodyRequestWrapper repeatable) {
        return repeatable;
      }
      if (current instanceof HttpServletRequestWrapper wrapper) {
        current = (HttpServletRequest) wrapper.getRequest();
      } else {
        break;
      }
    }
    return null;
  }
}
