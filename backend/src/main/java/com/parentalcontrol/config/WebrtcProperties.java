package com.parentalcontrol.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.webrtc")
public class WebrtcProperties {

  private List<String> stunUrls = new ArrayList<>(List.of("stun:stun.l.google.com:19302"));

  /** Primary TURN server URL list (all protocols for the same host). */
  private List<String> turnUrls = new ArrayList<>();

  /** Secondary TURN server URL list — used for geographic redundancy/failover. */
  private List<String> turnUrls2 = new ArrayList<>();

  /** Static credentials — dev only when {@link Turn#secret} is unset. */
  private String turnUsername = "";

  private String turnCredential = "";
  private Turn turn = new Turn();

  @Data
  public static class Turn {
    /** Shared secret matching coturn {@code static-auth-secret} (TURN REST API). */
    private String secret = "";

    private String realm = "pcp";

    /**
     * Ephemeral credential lifetime in seconds. Default 3600 (1 hour) — short enough that TURN
     * secret rotation takes effect within one credential cycle without long outage windows.
     * (Previous default of 86400 meant a rotated secret caused up to 24h of streaming failures for
     * devices holding the old credential.)
     */
    private long credentialTtlSeconds = 3_600;
  }

  public boolean isEphemeralCredentialsEnabled() {
    return turn != null && turn.getSecret() != null && !turn.getSecret().isBlank();
  }

  public boolean hasTurnUrls() {
    return turnUrls != null && !turnUrls.isEmpty();
  }

  public boolean hasSecondaryTurnUrls() {
    return turnUrls2 != null && !turnUrls2.isEmpty();
  }
}
