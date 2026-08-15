package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import java.util.Locale;

/**
 * Maps raw child/parent failure strings to stable reason codes for parent UI and funnel metrics.
 */
public final class StreamFailureReasonClassifier {

  public static final String CHILD_NO_OFFER = "CHILD_NO_OFFER";
  public static final String SIGNALING_REJECTED = "SIGNALING_REJECTED";
  public static final String ICE_FAILED = "ICE_FAILED";
  public static final String CONNECTION_FAILED = "CONNECTION_FAILED";

  private StreamFailureReasonClassifier() {}

  /**
   * @param status session status at failure time (PENDING ⇒ no child offer yet)
   */
  public static String classify(String rawReason, ScreenSessionStatus status) {
    if (rawReason == null || rawReason.isBlank()) {
      return status == ScreenSessionStatus.PENDING ? CHILD_NO_OFFER : CONNECTION_FAILED;
    }
    String r = rawReason.toUpperCase(Locale.ROOT);

    // ICE_* variants → stable ICE_FAILED for parent UI / funnel (keep other structured codes).
    if (r.contains("ICE_") || r.contains("ICE FAILED") || r.startsWith("ICE")) {
      return ICE_FAILED;
    }

    // Preserve known structured codes from the child app (FGS_, PROJECTION_, …).
    if (looksLikeStructuredCode(rawReason)) {
      return rawReason.trim();
    }

    if (r.contains("DUPLICATE SIGNALING")
        || r.contains("SESSION IS NOT ACTIVE")
        || r.contains("SESSION HAS ENDED")
        || r.contains("FAILED TO SEND OFFER")
        || r.contains("SIGNALING API")
        || r.contains("STATUS CODE 400")) {
      return SIGNALING_REJECTED;
    }

    if (r.contains("MAXIMUM ATTEMPTS")
        || r.contains("COULDN’T CONNECT TO THE PARENT")
        || r.contains("COULDN'T CONNECT TO THE PARENT")
        || r.contains("ANSWER_TIMEOUT")
        || r.contains("ANSWER WAIT")) {
      return status == ScreenSessionStatus.PENDING ? CHILD_NO_OFFER : CONNECTION_FAILED;
    }

    if (status == ScreenSessionStatus.PENDING) {
      return CHILD_NO_OFFER;
    }
    return CONNECTION_FAILED;
  }

  private static boolean looksLikeStructuredCode(String raw) {
    String t = raw.trim();
    if (t.length() > 64 || t.contains(" ")) {
      return false;
    }
    // e.g. CAMERA_FGS_FAILED, FGS_TIMEOUT_CAMERA, NO_SCREEN_CAPTURE_CONSENT
    return t.equals(t.toUpperCase(Locale.ROOT)) || t.startsWith("FGS_");
  }
}
