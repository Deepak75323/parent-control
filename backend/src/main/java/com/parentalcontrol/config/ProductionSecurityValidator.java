package com.parentalcontrol.config;

import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.common.security.RateLimitService;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductionSecurityValidator implements InitializingBean {

  private static final int MIN_JWT_SECRET_BYTES = 32;
  private static final int MIN_ENCRYPTION_KEY_BYTES = 32;
  private static final List<String> KNOWN_WEAK_DB_PASSWORDS =
      List.of("pcp_secret", "password", "postgres");

  private final Environment environment;
  private final JwtProperties jwtProperties;
  private final HmacProperties hmacProperties;
  private final FcmProperties fcmProperties;
  private final EncryptionProperties encryptionProperties;
  private final StompBrokerProperties stompBrokerProperties;
  private final StompClusterProperties stompClusterProperties;
  private final WebrtcProperties webrtcProperties;
  private final PlayIntegrityProperties playIntegrityProperties;
  private final DeviceTrustProperties deviceTrustProperties;
  private final TamperMasterPinProperties tamperMasterPinProperties;
  private final CorsProperties corsProperties;
  private final WebSocketProperties webSocketProperties;
  private final AdminEndpointProperties adminEndpointProperties;
  private final RateLimitProperties rateLimitProperties;
  private final RateLimitService rateLimitService;
  private final ObservabilityProperties observabilityProperties;
  private final BillingProperties billingProperties;
  private final PasswordResetProperties passwordResetProperties;
  private final EmailVerificationProperties emailVerificationProperties;
  private final SmtpProperties smtpProperties;

  @Value("${spring.datasource.url:}")
  private String datasourceUrl;

  @Value("${spring.datasource.username:}")
  private String datasourceUsername;

  @Value("${spring.datasource.password:}")
  private String datasourcePassword;

  @Value("${springdoc.api-docs.enabled:true}")
  private boolean apiDocsEnabled;

  @Override
  public void afterPropertiesSet() {
    if (isProdProfile()) {
      validateProductionSecurity();
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    if (!isProdProfile()) {
      logNonProdSecurityPosture();
    } else {
      log.info("Production security validation passed");
    }
  }

  private void validateProductionSecurity() {
    requireNotBlank(
        jwtProperties.getSecret(), "JWT_SECRET must be set to a strong value in production");
    if (jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
      throw new IllegalStateException(
          "Refusing to start: JWT_SECRET must be at least "
              + MIN_JWT_SECRET_BYTES
              + " bytes in production");
    }
    if (!hmacProperties.isRequired()) {
      throw new IllegalStateException(
          "Refusing to start: app.hmac.required must be true in production");
    }
    if (isBlank(encryptionProperties.getKey())) {
      throw new IllegalStateException(
          "Refusing to start: APP_ENCRYPTION_KEY must be set in production");
    }
    if (encryptionProperties.getKey().getBytes(StandardCharsets.UTF_8).length
        < MIN_ENCRYPTION_KEY_BYTES) {
      throw new IllegalStateException(
          "Refusing to start: APP_ENCRYPTION_KEY must be at least "
              + MIN_ENCRYPTION_KEY_BYTES
              + " bytes in production");
    }
    validateBilling();
    validateDatabase();
    validateCorsAndWebSocket();
    validateRateLimiting();
    validateObservability();
    validatePasswordReset();
    validateEmailVerification();
    if (adminEndpointProperties.isEndpointsEnabled()) {
      throw new IllegalStateException(
          "Refusing to start: app.admin.endpoints-enabled must be false in production");
    }
    if (apiDocsEnabled) {
      throw new IllegalStateException(
          "Refusing to start: springdoc.api-docs.enabled must be false in production");
    }
    if (!fcmProperties.isEnabled()) {
      log.warn("FCM is disabled in production — Doze/offline command delivery will be delayed");
    } else {
      boolean hasV1 =
          fcmProperties.getServiceAccountFile() != null
              && !fcmProperties.getServiceAccountFile().isBlank();
      boolean hasLegacy =
          fcmProperties.getServerKey() != null && !fcmProperties.getServerKey().isBlank();
      if (!hasV1 && !hasLegacy) {
        throw new IllegalStateException(
            "Refusing to start: FCM_ENABLED=true requires FCM_SERVICE_ACCOUNT_FILE or FCM_SERVER_KEY");
      }
    }
    if (!stompBrokerProperties.isRelayEnabled()
        && stompClusterProperties.getMode() != StompClusterProperties.Mode.REDIS) {
      throw new IllegalStateException(
          "Refusing to start: enable app.stomp.cluster.mode=redis or app.stomp.relay-enabled=true "
              + "in production (in-memory broker cannot scale past one replica)");
    }
    if (!webrtcProperties.isEphemeralCredentialsEnabled()) {
      throw new IllegalStateException(
          "Refusing to start: app.webrtc.turn.secret (TURN_SECRET) must be set in production "
              + "for ephemeral TURN credentials");
    }
    if (!webrtcProperties.hasTurnUrls()) {
      throw new IllegalStateException(
          "Refusing to start: app.webrtc.turn-urls must be configured in production");
    }
    String loopbackTurn = findLoopbackTurnUrl();
    if (loopbackTurn != null) {
      throw new IllegalStateException(
          "Refusing to start: TURN URL '"
              + loopbackTurn
              + "' points to a loopback "
              + "address. Cross-network WebRTC traffic cannot use 127.0.0.1/localhost. "
              + "Set TURN_EXTERNAL_HOST to a publicly reachable hostname.");
    }
    if (playIntegrityProperties.isRequired()) {
      boolean hasSa =
          playIntegrityProperties.getServiceAccountFile() != null
              && !playIntegrityProperties.getServiceAccountFile().isBlank();
      if (!hasSa) {
        throw new IllegalStateException(
            "Refusing to start: PLAY_INTEGRITY_SERVICE_ACCOUNT_FILE required when "
                + "play-integrity is enabled in production");
      }
    }
    if (deviceTrustProperties.isEnforcementEnabled() && !playIntegrityProperties.isRequired()) {
      throw new IllegalStateException(
          "Refusing to start: device-trust enforcement requires play-integrity.required=true");
    }
    if (tamperMasterPinProperties.isEnabled()) {
      throw new IllegalStateException(
          "Refusing to start: tamper.master-pin.enabled must be false in production");
    }
    if (!isBlank(tamperMasterPinProperties.getPin())) {
      throw new IllegalStateException(
          "Refusing to start: TAMPER_MASTER_PIN must not be set in production");
    }
  }

  private void validateBilling() {
    if (!billingProperties.isEnforcementEnabled()) {
      throw new IllegalStateException(
          "Refusing to start: app.billing.enforcement-enabled must be true in production");
    }
    requireNotBlank(
        billingProperties.getStripeSecretKey(),
        "STRIPE_SECRET_KEY must be set when BILLING_ENFORCEMENT=true in production");
    requireNotBlank(
        billingProperties.getStripeWebhookSecret(),
        "STRIPE_WEBHOOK_SECRET must be set when BILLING_ENFORCEMENT=true in production");
    if (billingProperties.isRazorpayEnabled()) {
      throw new IllegalStateException(
          "Refusing to start: app.billing.razorpay-enabled must be false in production "
              + "until Razorpay signature verification is implemented");
    }
  }

  private void validateDatabase() {
    requireNotBlank(datasourceUrl, "SPRING_DATASOURCE_URL must be set in production");
    requireNotBlank(datasourceUsername, "SPRING_DATASOURCE_USERNAME must be set in production");
    requireNotBlank(datasourcePassword, "SPRING_DATASOURCE_PASSWORD must be set in production");
    if (KNOWN_WEAK_DB_PASSWORDS.contains(datasourcePassword)) {
      throw new IllegalStateException(
          "Refusing to start: database password must not use a known default value in production");
    }
    String urlLower = datasourceUrl.toLowerCase();
    boolean sslConfigured =
        urlLower.contains("sslmode=require")
            || urlLower.contains("sslmode=verify-full")
            || urlLower.contains("sslmode=verify-ca")
            || urlLower.contains("ssl=true");
    if (!sslConfigured) {
      throw new IllegalStateException(
          "Refusing to start: SPRING_DATASOURCE_URL must enable TLS "
              + "(e.g. sslmode=require) in production");
    }
  }

  private void validateCorsAndWebSocket() {
    List<String> corsOrigins = corsProperties.getAllowedOrigins();
    if (corsOrigins == null || corsOrigins.isEmpty()) {
      throw new IllegalStateException(
          "Refusing to start: CORS_ALLOWED_ORIGINS must list your frontend domain(s) in production");
    }
    for (String origin : corsOrigins) {
      if (origin == null || origin.isBlank() || "*".equals(origin.trim())) {
        throw new IllegalStateException(
            "Refusing to start: CORS must not use wildcard (*) in production");
      }
    }
    List<String> wsOrigins = webSocketProperties.getAllowedOriginPatterns();
    if (wsOrigins == null || wsOrigins.isEmpty()) {
      throw new IllegalStateException(
          "Refusing to start: WEBSOCKET_ALLOWED_ORIGINS must be set in production");
    }
    for (String pattern : wsOrigins) {
      if (pattern == null || pattern.isBlank() || "*".equals(pattern.trim())) {
        throw new IllegalStateException(
            "Refusing to start: WebSocket allowed origins must not use wildcard (*) in production");
      }
    }
  }

  private void validateRateLimiting() {
    if (!rateLimitProperties.isEnabled()) {
      throw new IllegalStateException(
          "Refusing to start: app.rate-limit.enabled must be true in production");
    }
    if (!rateLimitProperties.isFailClosed()) {
      throw new IllegalStateException(
          "Refusing to start: app.rate-limit.fail-closed must be true in production");
    }
    if (rateLimitProperties.getAuthRequestsPerMinute() > 5) {
      throw new IllegalStateException(
          "Refusing to start: auth rate limit must be ≤ 5 requests/minute in production");
    }
    if (!rateLimitService.isRedisConnected()) {
      throw new IllegalStateException(
          "Refusing to start: Redis must be reachable when rate limiting is fail-closed in production");
    }
  }

  private void validateObservability() {
    if (observabilityProperties.isPrometheusPublic()) {
      throw new IllegalStateException(
          "Refusing to start: app.observability.prometheus-public must be false in production");
    }
  }

  private void validateEmailVerification() {
    if (!emailVerificationProperties.isEnforcementEnabled()) {
      throw new IllegalStateException(
          "Refusing to start: app.email-verification.enforcement-enabled must be true in production");
    }
    requireNotBlank(
        emailVerificationProperties.getVerifyUrlBase(),
        "EMAIL_VERIFICATION_URL_BASE must be set when email verification is enforced in production");
    requireNotBlank(
        smtpProperties.getHost(),
        "SMTP_HOST must be set when email verification is enforced in production");
  }

  private void validatePasswordReset() {
    if (!passwordResetProperties.isEnabled()) {
      return;
    }
    if (passwordResetProperties.getTokenTtlMinutes() > 15) {
      throw new IllegalStateException(
          "Refusing to start: password reset token TTL must be ≤ 15 minutes in production");
    }
    requireNotBlank(
        passwordResetProperties.getResetUrlBase(),
        "PASSWORD_RESET_URL_BASE must be set when password reset is enabled in production");
    requireNotBlank(
        smtpProperties.getHost(),
        "SMTP_HOST must be set when password reset is enabled in production");
  }

  private void logNonProdSecurityPosture() {
    java.util.List<String> warnings = new java.util.ArrayList<>();
    if (isBlank(jwtProperties.getSecret())) {
      warnings.add("JWT_SECRET is unset — set in backend/.env for local dev");
    }
    if (!hmacProperties.isRequired()) {
      warnings.add("app.hmac.required=false — device requests are not signature-verified");
    }
    if (isBlank(encryptionProperties.getKey())) {
      warnings.add("APP_ENCRYPTION_KEY is unset — encryption at rest disabled");
    }
    if (!webrtcProperties.isEphemeralCredentialsEnabled()) {
      warnings.add("TURN_SECRET is empty — ephemeral TURN credentials disabled");
    }
    if (!webrtcProperties.hasTurnUrls()) {
      warnings.add("app.webrtc.turn-urls is empty — WebRTC will fail across NATs");
    } else if (findLoopbackTurnUrl() != null) {
      warnings.add("TURN URL points to loopback — only works for same-host testing");
    }
    if (tamperMasterPinProperties.isEnabled() && !isBlank(tamperMasterPinProperties.getPin())) {
      warnings.add("TAMPER_MASTER_PIN is enabled — disable before production release");
    }
    if (adminEndpointProperties.isEndpointsEnabled()) {
      warnings.add(
          "app.admin.endpoints-enabled=true — admin chaos/certification routes are exposed");
    }
    if (warnings.isEmpty()) {
      return;
    }
    log.warn("==================== INSECURE DEV DEFAULTS DETECTED ====================");
    for (String w : warnings) {
      log.warn(" - {}", w);
    }
    log.warn(
        "Active profiles: {}. Set spring.profiles.active=prod to enforce these checks.",
        Arrays.toString(environment.getActiveProfiles()));
    log.warn("=========================================================================");
  }

  private static void requireNotBlank(String value, String message) {
    if (isBlank(value)) {
      throw new IllegalStateException("Refusing to start: " + message);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String findLoopbackTurnUrl() {
    java.util.List<String> all = new java.util.ArrayList<>();
    if (webrtcProperties.getTurnUrls() != null) all.addAll(webrtcProperties.getTurnUrls());
    if (webrtcProperties.getTurnUrls2() != null) all.addAll(webrtcProperties.getTurnUrls2());
    for (String url : all) {
      if (url == null) continue;
      String lower = url.toLowerCase();
      if (lower.contains("127.0.0.1") || lower.contains("localhost") || lower.contains("::1")) {
        return url;
      }
    }
    return null;
  }

  private boolean isProdProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains("prod");
  }
}
