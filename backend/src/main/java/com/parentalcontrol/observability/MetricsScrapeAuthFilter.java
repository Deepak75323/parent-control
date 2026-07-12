package com.parentalcontrol.observability;

import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.config.ObservabilityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Allows Prometheus scrape of /actuator/prometheus via X-Metrics-Token or when explicitly public
 * (dev).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
@RequiredArgsConstructor
public class MetricsScrapeAuthFilter extends OncePerRequestFilter {

  private final ObservabilityProperties observabilityProperties;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(ApiConstants.ACTUATOR_PROMETHEUS);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (observabilityProperties.isPrometheusPublic()) {
      filterChain.doFilter(request, response);
      return;
    }
    String token = observabilityProperties.getMetricsScrapeToken();
    if (token != null && !token.isBlank()) {
      String header = request.getHeader("X-Metrics-Token");
      String auth = request.getHeader("Authorization");
      if (token.equals(header) || ("Bearer " + token).equals(auth)) {
        filterChain.doFilter(request, response);
        return;
      }
    }
    response.setStatus(HttpStatus.FORBIDDEN.value());
  }
}
