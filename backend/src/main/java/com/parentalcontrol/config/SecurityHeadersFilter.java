package com.parentalcontrol.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds security headers to every HTTP response (Spring equivalent of helmet). */
@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 4)
@RequiredArgsConstructor
public class SecurityHeadersFilter extends OncePerRequestFilter {

  private final Environment environment;
  private final CorsProperties corsProperties;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

    if (isProdProfile()) {
      response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }

    String csp = corsProperties.getContentSecurityPolicy();
    if (csp != null && !csp.isBlank()) {
      response.setHeader("Content-Security-Policy", csp);
    }

    filterChain.doFilter(request, response);
  }

  private boolean isProdProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains("prod");
  }
}
