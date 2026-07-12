package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminEndpointProperties {

  /** Chaos/certification ingest endpoints — disabled in production by default. */
  private boolean endpointsEnabled = false;
}
