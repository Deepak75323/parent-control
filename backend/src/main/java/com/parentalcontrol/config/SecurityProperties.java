package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Step-up / sensitive-action security toggles.
 *
 * <p>Defaults are intentionally permissive so developer setups keep working without a parent
 * PIN; the production profile ({@code application-prod.yml}) flips them on.
 */
@Data
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /**
     * Require the parent PIN ({@code parentPin} in the command body, or a prior
     * /api/v1/parent/pin/verify call in this session) for sensitive device commands:
     * TAP, SWIPE, TYPE_TEXT, LAUNCH_APP, ENABLE_KIOSK, DISABLE_KIOSK, REVOKE_MONITORING.
     */
    private boolean remoteControlPinRequired = false;
}
