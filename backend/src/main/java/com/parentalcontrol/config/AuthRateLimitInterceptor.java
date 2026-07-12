package com.parentalcontrol.config;

import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.common.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthRateLimitInterceptor implements HandlerInterceptor {

  private final RateLimitService rateLimitService;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    String clientKey = resolveClientKey(request);

    if (ApiConstants.AUTH_FORGOT_PASSWORD.equals(path) && "POST".equalsIgnoreCase(method)) {
      rateLimitService.checkPasswordReset(clientKey);
      return true;
    }
    if (ApiConstants.AUTH_RESET_PASSWORD.equals(path) && "POST".equalsIgnoreCase(method)) {
      rateLimitService.checkAuth(clientKey);
      return true;
    }
    if (ApiConstants.AUTH_ACCOUNT.equals(path) && "DELETE".equalsIgnoreCase(method)) {
      rateLimitService.checkAuth(clientKey);
      return true;
    }
    if (ApiConstants.DEVICE_PAIR.equals(path) && "POST".equalsIgnoreCase(method)) {
      rateLimitService.checkPairing(clientKey);
      return true;
    }
    if (path.startsWith(ApiConstants.ONBOARDING_PREFIX) && "POST".equalsIgnoreCase(method)) {
      rateLimitService.checkOnboarding(clientKey);
      return true;
    }
    if (ApiConstants.BILLING_WEBHOOKS_STRIPE.equals(path) && "POST".equalsIgnoreCase(method)) {
      rateLimitService.checkWebhook(clientKey);
      return true;
    }
    if (ApiConstants.DEVICES_PAIRING_CODE.equals(path) && "POST".equalsIgnoreCase(method)) {
      rateLimitService.checkPairingCodeCreation(clientKey);
      return true;
    }
    if (path.startsWith(ApiConstants.AUTH_PREFIX) && "POST".equalsIgnoreCase(method)) {
      rateLimitService.checkAuth(clientKey);
    }
    return true;
  }

  private static String resolveClientKey(HttpServletRequest request) {
    // Use RemoteAddr only — in prod set server.forward-headers-strategy=framework so
    // Spring adjusts this from trusted proxy headers (not client-spoofable X-Forwarded-For).
    return request.getRemoteAddr();
  }
}
