package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

  private boolean enabled = true;

  /** Login, register, refresh — per IP per minute. Production default: 5. */
  private int authRequestsPerMinute = 30;

  /** Password reset request limit — 3 per hour per IP in production. */
  private int passwordResetPerHour = 3;

  private int deviceIngestRequestsPerMinute = 120;

  /** Tamper PIN/code verification — per device per minute. */
  private int tamperVerifyPerMinute = 10;

  /** Tamper disable attempts — per device per minute. */
  private int tamperAttemptPerMinute = 5;

  /** Stripe/Razorpay webhooks — per IP per minute. */
  private int webhookRequestsPerMinute = 60;

  /** Parent pairing-code generation — per IP per minute. */
  private int pairingCodeCreationPerMinute = 10;

  /** When true (production), rate limiting fails closed if Redis is unavailable. */
  private boolean failClosed = false;
}
