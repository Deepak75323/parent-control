package com.parentalcontrol.config;

import com.parentalcontrol.common.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductionSecurityValidator {

    private static final String DEFAULT_JWT_SECRET =
            "change-me-in-production-use-256-bit-minimum-secret-key!!";
    private static final String DEFAULT_ENCRYPTION_KEY =
            "dev-only-change-in-production-32chars!!";

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

    @EventListener(ApplicationReadyEvent.class)
    public void validateProductionSecurity() {
        if (!isProdProfile()) {
            logNonProdSecurityPosture();
            return;
        }
        if (DEFAULT_JWT_SECRET.equals(jwtProperties.getSecret())) {
            throw new IllegalStateException(
                    "Refusing to start: JWT_SECRET must be set to a strong value in production");
        }
        if (!hmacProperties.isRequired()) {
            throw new IllegalStateException(
                    "Refusing to start: app.hmac.required must be true in production");
        }
        if (DEFAULT_ENCRYPTION_KEY.equals(encryptionProperties.getKey())) {
            throw new IllegalStateException(
                    "Refusing to start: APP_ENCRYPTION_KEY must be set in production");
        }
        if (!fcmProperties.isEnabled()) {
            log.warn("FCM is disabled in production — Doze/offline command delivery will be delayed");
        } else {
            boolean hasV1 = fcmProperties.getServiceAccountFile() != null
                    && !fcmProperties.getServiceAccountFile().isBlank();
            boolean hasLegacy = fcmProperties.getServerKey() != null
                    && !fcmProperties.getServerKey().isBlank();
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
                    "Refusing to start: TURN URL '" + loopbackTurn + "' points to a loopback "
                            + "address. Cross-network WebRTC traffic cannot use 127.0.0.1/localhost. "
                            + "Set TURN_EXTERNAL_HOST to a publicly reachable hostname.");
        }
        if (playIntegrityProperties.isRequired()) {
            boolean hasSa = playIntegrityProperties.getServiceAccountFile() != null
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
            log.warn(
                    "tamper.master-pin.enabled=true in production — disable and use a server-managed secret before release");
        }
        log.info("Production security validation passed");
    }

    /**
     * Visibility for non-prod runs: surface insecure defaults that would block prod boot so
     * developers see the difference before deployment. Never throws — dev/test must stay loose.
     */
    private void logNonProdSecurityPosture() {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (DEFAULT_JWT_SECRET.equals(jwtProperties.getSecret())) {
            warnings.add("JWT_SECRET is the dev default — would block production boot");
        }
        if (!hmacProperties.isRequired()) {
            warnings.add("app.hmac.required=false — device requests are not signature-verified");
        }
        if (DEFAULT_ENCRYPTION_KEY.equals(encryptionProperties.getKey())) {
            warnings.add("APP_ENCRYPTION_KEY is the dev default");
        }
        if (!webrtcProperties.isEphemeralCredentialsEnabled()) {
            warnings.add("TURN_SECRET is empty — ephemeral TURN credentials disabled");
        }
        if (!webrtcProperties.hasTurnUrls()) {
            warnings.add("app.webrtc.turn-urls is empty — WebRTC will fail across NATs");
        } else if (findLoopbackTurnUrl() != null) {
            warnings.add("TURN URL points to loopback — only works for same-host testing");
        }
        if (warnings.isEmpty()) {
            return;
        }
        log.warn("==================== INSECURE DEV DEFAULTS DETECTED ====================");
        for (String w : warnings) {
            log.warn(" - {}", w);
        }
        log.warn("Active profiles: {}. Set spring.profiles.active=prod to enforce these checks.",
                Arrays.toString(environment.getActiveProfiles()));
        log.warn("Admin master PIN may be enabled (tamper.master-pin.enabled) — disable before production release.");
        log.warn("=========================================================================");
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
