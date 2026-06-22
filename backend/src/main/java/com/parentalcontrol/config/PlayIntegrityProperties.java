package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.play-integrity")
public class PlayIntegrityProperties {

    /** When true, pair/heartbeat must include a valid integrity token. */
    private boolean required = false;

    /** Android application id registered in Play Console. */
    private String packageName = "com.childmonitor";

    /** OAuth access token with androidpublisher / Play Integrity scope (short-lived). */
    private String googleCloudAccessToken = "";

    /** Service account JSON for Play Integrity API (preferred in production). */
    private String serviceAccountFile = "";

    /** Reject sideload / unknown recognition verdicts. */
    private boolean requirePlayRecognized = false;

    /** Require MEETS_STRONG_INTEGRITY device verdict (hardware-backed). */
    private boolean requireStrongIntegrity = false;
}
