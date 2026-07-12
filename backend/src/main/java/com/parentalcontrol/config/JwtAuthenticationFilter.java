package com.parentalcontrol.config;

import com.parentalcontrol.auth.service.UserTokenInvalidationService;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.common.constant.HeaderConstants;
import com.parentalcontrol.common.domain.TokenType;
import com.parentalcontrol.common.security.AuthenticatedUser;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.device.security.DeviceTokenValidationService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Set<String> PUBLIC_EXACT =
      Set.of(
          ApiConstants.AUTH_REGISTER,
          ApiConstants.AUTH_LOGIN,
          ApiConstants.AUTH_REFRESH,
          ApiConstants.AUTH_LOGOUT,
          ApiConstants.AUTH_FORGOT_PASSWORD,
          ApiConstants.AUTH_RESET_PASSWORD,
          ApiConstants.AUTH_VERIFY_EMAIL,
          ApiConstants.DEVICE_PAIR);
  private static final Set<String> PUBLIC_PREFIX =
      Set.of(
          ApiConstants.BILLING_WEBHOOKS_PREFIX,
          ApiConstants.ONBOARDING_PREFIX,
          ApiConstants.SWAGGER_UI,
          ApiConstants.API_DOCS,
          ApiConstants.V3_API_DOCS,
          ApiConstants.ACTUATOR_HEALTH,
          ApiConstants.ACTUATOR_INFO,
          ApiConstants.WS,
          ApiConstants.WS_SLASH);

  private final JwtTokenProvider jwtTokenProvider;
  private final DeviceTokenValidationService deviceTokenValidationService;
  private final UserTokenInvalidationService userTokenInvalidationService;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();

    if (isPublic(path)) {
      filterChain.doFilter(request, response);
      return;
    }

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(HeaderConstants.BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = header.substring(HeaderConstants.BEARER_PREFIX.length());
    if (!jwtTokenProvider.validateToken(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    Claims claims = jwtTokenProvider.parseClaims(token);
    TokenType tokenType = jwtTokenProvider.getTokenType(claims);

    if (tokenType == TokenType.REFRESH && !ApiConstants.AUTH_REFRESH.equals(path)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    if (tokenType == TokenType.ACCESS || tokenType == TokenType.REFRESH) {
      UUID userId = jwtTokenProvider.getUserId(claims);
      if (userTokenInvalidationService.isParentTokenRejected(userId, claims.getIssuedAt())) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
    }

    if (path.startsWith(ApiConstants.DEVICE_PREFIX) && tokenType != TokenType.DEVICE) {
      filterChain.doFilter(request, response);
      return;
    }
    if (tokenType == TokenType.DEVICE) {
      try {
        deviceTokenValidationService.validateDeviceToken(claims);
      } catch (Exception e) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
    }
    if (isParentMonitoringPath(path) && tokenType != TokenType.ACCESS) {
      filterChain.doFilter(request, response);
      return;
    }

    AuthenticatedUser principal =
        AuthenticatedUser.builder()
            .id(jwtTokenProvider.getUserId(claims))
            .email(claims.get("email", String.class))
            .role(jwtTokenProvider.getRole(claims))
            .deviceId(jwtTokenProvider.getDeviceId(claims))
            .build();

    var authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    filterChain.doFilter(request, response);
  }

  private boolean isPublic(String path) {
    if (PUBLIC_EXACT.contains(path)) {
      return true;
    }
    return PUBLIC_PREFIX.stream().anyMatch(path::startsWith);
  }

  /**
   * Parent-only monitoring paths; signaling + webrtc-config are shared with child device tokens.
   */
  private boolean isParentMonitoringPath(String path) {
    if (path.startsWith(ApiConstants.MONITORING_SIGNALING_PREFIX)) {
      return false;
    }
    if (ApiConstants.MONITORING_WEBRTC_CONFIG.equals(path)) {
      return false;
    }
    if (ApiConstants.MONITORING_WEBRTC_ICE_REPORT.equals(path)) {
      return false;
    }
    return path.startsWith(ApiConstants.DEVICES)
        || path.startsWith(ApiConstants.MONITORING)
        || path.startsWith(ApiConstants.POLICIES);
  }
}
