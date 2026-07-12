package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.hmac")
public class HmacProperties {

  /** When false, device HMAC headers are optional (local dev). */
  private boolean required = false;

  private long maxTimestampSkewSeconds = 300;
}
