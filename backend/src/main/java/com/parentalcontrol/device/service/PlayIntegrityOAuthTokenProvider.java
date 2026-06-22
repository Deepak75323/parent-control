package com.parentalcontrol.device.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.parentalcontrol.config.PlayIntegrityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * OAuth2 access tokens for Play Integrity API ({@code playintegrity} scope).
 */
@Component
@Slf4j
public class PlayIntegrityOAuthTokenProvider {

    private static final List<String> SCOPES =
            List.of("https://www.googleapis.com/auth/playintegrity");

    private final PlayIntegrityProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile GoogleCredentials credentials;

    public PlayIntegrityOAuthTokenProvider(PlayIntegrityProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        String path = properties.getServiceAccountFile();
        return path != null && !path.isBlank();
    }

    public String getAccessToken() throws IOException {
        if (isConfigured()) {
            ensureServiceAccountInitialized();
            synchronized (this) {
                credentials.refreshIfExpired();
                return credentials.getAccessToken().getTokenValue();
            }
        }
        String staticToken = properties.getGoogleCloudAccessToken();
        if (staticToken != null && !staticToken.isBlank()) {
            return staticToken;
        }
        throw new IllegalStateException("Play Integrity credentials not configured");
    }

    private void ensureServiceAccountInitialized() throws IOException {
        if (credentials != null) {
            return;
        }
        synchronized (this) {
            if (credentials != null) {
                return;
            }
            String path = properties.getServiceAccountFile();
            try (InputStream in = new FileInputStream(path)) {
                credentials = GoogleCredentials.fromStream(in).createScoped(SCOPES);
            }
            log.info("Play Integrity service account credentials loaded");
        }
    }
}
