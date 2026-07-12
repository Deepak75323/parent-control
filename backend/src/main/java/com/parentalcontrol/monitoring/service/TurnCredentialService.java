package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.common.constant.TimeoutConstants;
import com.parentalcontrol.config.WebrtcProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Issues time-limited TURN credentials per the coturn REST API (HMAC-SHA1).
 *
 * <p>Username format:
 *
 * <ul>
 *   <li>Subject-scoped: {@code {expiry_unix}:{subjectId}} — used when no stream session is in
 *       flight (default lifetime from {@code app.webrtc.turn.credential-ttl-seconds}).
 *   <li>Session-bound (P2-4): {@code {expiry_unix}:{subjectId}:{sessionId}} with a short TTL (15
 *       min default) so a leaked credential can only relay a single stream session, not arbitrary
 *       streams for the parent's full session lifetime.
 * </ul>
 *
 * <p>coturn validates the timestamp and HMAC only — it does not enforce the username structure —
 * but embedding the sessionId makes leaked credentials trivially correlatable to a specific stream
 * session for incident response, and the short TTL bounds blast radius.
 */
@Service
@RequiredArgsConstructor
public class TurnCredentialService {

  /** P2-4: Session-bound credentials live only as long as a typical streaming session. */
  private static final long SESSION_BOUND_TTL_SECONDS = TimeoutConstants.TURN_SESSION_TTL_SECONDS;

  private final WebrtcProperties webrtcProperties;

  public EphemeralTurnCredential issue(UUID subjectId) {
    return issue(subjectId, null);
  }

  /**
   * Issue a TURN credential, optionally bound to a streaming session.
   *
   * @param subjectId parent user or child device UUID requesting the credential
   * @param sessionId optional ScreenSession UUID; when present, credential is session-scoped
   */
  public EphemeralTurnCredential issue(UUID subjectId, UUID sessionId) {
    WebrtcProperties.Turn turn = webrtcProperties.getTurn();
    long ttlSeconds =
        sessionId != null
            ? Math.min(SESSION_BOUND_TTL_SECONDS, turn.getCredentialTtlSeconds())
            : turn.getCredentialTtlSeconds();
    long expiryEpochSeconds = Instant.now().getEpochSecond() + ttlSeconds;
    String username =
        sessionId != null
            ? expiryEpochSeconds + ":" + subjectId + ":" + sessionId
            : expiryEpochSeconds + ":" + subjectId;
    String credential = computeCredential(turn.getSecret(), username);
    return new EphemeralTurnCredential(username, credential, expiryEpochSeconds, ttlSeconds);
  }

  static String computeCredential(String secret, String username) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
      byte[] digest = mac.doFinal(username.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to compute TURN credential", e);
    }
  }

  public record EphemeralTurnCredential(
      String username, String credential, long expiresAtEpochSeconds, long ttlSeconds) {}
}
