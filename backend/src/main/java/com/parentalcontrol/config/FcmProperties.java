package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.fcm")
public class FcmProperties {

  /** When true, sends FCM data messages to wake child devices. */
  private boolean enabled = false;

  /**
   * Path to Firebase service account JSON (FCM HTTP v1). Preferred for new Firebase projects —
   * legacy server keys are no longer issued.
   */
  private String serviceAccountFile = "";

  /** Legacy FCM server key — only if your Firebase project still has the Legacy API enabled. */
  private String serverKey = "";
}
