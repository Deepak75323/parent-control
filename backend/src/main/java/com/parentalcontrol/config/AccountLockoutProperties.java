package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.account-lockout")
public class AccountLockoutProperties {

  /** Enable per-account failed-login tracking and automatic lockout. */
  private boolean enabled = true;

  /** Number of consecutive failed login attempts before the account is locked. Default: 5. */
  private int maxAttempts = 5;

  /**
   * Sliding window (seconds) over which failed attempts are counted. The counter resets after this
   * duration of inactivity. Default: 900 s (15 min).
   */
  private int attemptWindowSeconds = 900;

  /**
   * Duration (seconds) that the account is locked once maxAttempts is exceeded. The lock expires
   * automatically via a Redis key TTL — no cron job required. Default: 900 s (15 min).
   */
  private int lockDurationSeconds = 900;

  /**
   * When true, a Redis unavailability blocks login (fail-closed / secure default). When false
   * (default), a Redis failure is logged and lockout tracking is skipped so legitimate users are
   * not locked out during a cache outage.
   */
  private boolean failClosed = false;
}
