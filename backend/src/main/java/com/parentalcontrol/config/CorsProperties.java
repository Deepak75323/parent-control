package com.parentalcontrol.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

  /** Comma-separated in env: CORS_ALLOWED_ORIGINS=https://app.example.com,http://localhost:5173 */
  private List<String> allowedOrigins = new ArrayList<>();

  /**
   * CSP for API responses (relevant if any browser client hits the API directly). Restrict scripts
   * to your frontend origin(s).
   */
  private String contentSecurityPolicy =
      "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = splitOrigins(allowedOrigins);
  }

  public void setContentSecurityPolicy(String contentSecurityPolicy) {
    this.contentSecurityPolicy = contentSecurityPolicy;
  }

  private static List<String> splitOrigins(List<String> origins) {
    if (origins == null || origins.isEmpty()) {
      return new ArrayList<>();
    }
    return origins.stream()
        .filter(origin -> origin != null && !origin.isBlank())
        .flatMap(origin -> Arrays.stream(origin.split(",")))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .collect(Collectors.toCollection(ArrayList::new));
  }
}
