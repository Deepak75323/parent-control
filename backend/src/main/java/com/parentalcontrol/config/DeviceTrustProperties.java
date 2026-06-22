package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.device-trust")
public class DeviceTrustProperties {

    /** Enforce trust scoring on pair/heartbeat. */
    private boolean enforcementEnabled = false;

    /** Minimum score (0-100) required to complete pairing. */
    private int minPairingScore = 80;

    /** Minimum score to maintain an active session. */
    private int minSessionScore = 50;

    /** Block session when score drops below this threshold. */
    private int blockScore = 20;

    /** Max age of integrity nonce embedded in token (seconds). */
    private long nonceMaxAgeSeconds = 300;
}
