package com.parentalcontrol.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

  /** Comma-separated in env: CORS_ALLOWED_ORIGINS=https://app.example.com */
  private List<String> allowedOrigins = new ArrayList<>();

  /**
   * CSP for API responses (relevant if any browser client hits the API directly). Restrict scripts
   * to your frontend origin(s).
   */
  private String contentSecurityPolicy =
      "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";
}
