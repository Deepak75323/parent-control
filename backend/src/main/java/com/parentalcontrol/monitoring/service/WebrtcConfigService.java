package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.config.WebrtcProperties;
import com.parentalcontrol.monitoring.dto.WebrtcIceConfigResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebrtcConfigService {

    private final WebrtcProperties webrtcProperties;
    private final TurnCredentialService turnCredentialService;
    private final MeterRegistry meterRegistry;

    public WebrtcIceConfigResponse buildIceConfig(UUID subjectId) {
        return buildIceConfig(subjectId, null);
    }

    /**
     * P2-4: when {@code sessionId} is non-null, TURN credentials are bound to that streaming
     * session with a short (≤15 min) TTL — a leaked credential then cannot relay arbitrary
     * future streams, only this single session.
     */
    public WebrtcIceConfigResponse buildIceConfig(UUID subjectId, UUID sessionId) {
        List<WebrtcIceConfigResponse.IceServerDto> servers = new ArrayList<>();

        if (!webrtcProperties.getStunUrls().isEmpty()) {
            servers.add(WebrtcIceConfigResponse.IceServerDto.builder()
                    .urls(webrtcProperties.getStunUrls())
                    .build());
        }

        Long credentialExpiresAt = null;
        Long credentialTtlSeconds = null;

        if (webrtcProperties.hasTurnUrls()) {
            if (webrtcProperties.isEphemeralCredentialsEnabled()) {
                // Primary TURN server — always issued when secret is configured.
                TurnCredentialService.EphemeralTurnCredential cred =
                        turnCredentialService.issue(subjectId, sessionId);
                servers.add(WebrtcIceConfigResponse.IceServerDto.builder()
                        .urls(webrtcProperties.getTurnUrls())
                        .username(cred.username())
                        .credential(cred.credential())
                        .build());
                credentialExpiresAt = cred.expiresAtEpochSeconds();
                credentialTtlSeconds = cred.ttlSeconds();
                String scope = sessionId != null ? "session" : "subject";
                meterRegistry.counter(
                        "turn.credentials.issued",
                        "mode", "ephemeral",
                        "server", "primary",
                        "scope", scope).increment();

                // Secondary TURN server — same TTL/expiry as primary but independent server.
                // Clients will attempt both in parallel; whichever gathers relay candidates first wins.
                if (webrtcProperties.hasSecondaryTurnUrls()) {
                    servers.add(WebrtcIceConfigResponse.IceServerDto.builder()
                            .urls(webrtcProperties.getTurnUrls2())
                            .username(cred.username())
                            .credential(cred.credential())
                            .build());
                    meterRegistry.counter(
                            "turn.credentials.issued",
                            "mode", "ephemeral",
                            "server", "secondary",
                            "scope", scope).increment();
                }
            } else if (hasStaticTurnCredentials()) {
                servers.add(WebrtcIceConfigResponse.IceServerDto.builder()
                        .urls(webrtcProperties.getTurnUrls())
                        .username(webrtcProperties.getTurnUsername())
                        .credential(webrtcProperties.getTurnCredential())
                        .build());
                meterRegistry.counter("turn.credentials.issued", "mode", "static", "server", "primary").increment();
            }
        }

        return WebrtcIceConfigResponse.builder()
                .iceServers(servers)
                .credentialExpiresAt(credentialExpiresAt)
                .credentialTtlSeconds(credentialTtlSeconds)
                .build();
    }

    private boolean hasStaticTurnCredentials() {
        return webrtcProperties.getTurnUsername() != null
                && !webrtcProperties.getTurnUsername().isBlank()
                && webrtcProperties.getTurnCredential() != null
                && !webrtcProperties.getTurnCredential().isBlank();
    }
}
