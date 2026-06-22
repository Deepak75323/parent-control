package com.parentalcontrol.device.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.parentalcontrol.config.FcmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * OAuth2 access tokens for FCM HTTP v1 ({@code firebase.messaging} scope).
 */
@Component
@Slf4j
public class FcmOAuthTokenProvider {

    private static final List<String> SCOPES =
            List.of("https://www.googleapis.com/auth/firebase.messaging");

    private final FcmProperties fcmProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile GoogleCredentials credentials;
    private volatile String projectId;

    public FcmOAuthTokenProvider(FcmProperties fcmProperties) {
        this.fcmProperties = fcmProperties;
    }

    public boolean isConfigured() {
        String path = fcmProperties.getServiceAccountFile();
        return path != null && !path.isBlank();
    }

    public String getProjectId() throws IOException {
        ensureInitialized();
        return projectId;
    }

    public String getAccessToken() throws IOException {
        ensureInitialized();
        synchronized (this) {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        }
    }

    private void ensureInitialized() throws IOException {
        if (credentials != null) {
            return;
        }
        synchronized (this) {
            if (credentials != null) {
                return;
            }
            String path = fcmProperties.getServiceAccountFile();
            if (path == null || path.isBlank()) {
                throw new IllegalStateException("FCM service account file not configured");
            }
            try (InputStream in = new FileInputStream(path)) {
                JsonNode root = objectMapper.readTree(in);
                projectId = root.path("project_id").asText(null);
                if (projectId == null || projectId.isBlank()) {
                    throw new IllegalStateException("FCM service account JSON missing project_id");
                }
            }
            try (InputStream in = new FileInputStream(path)) {
                credentials = GoogleCredentials.fromStream(in).createScoped(SCOPES);
            }
            log.info("FCM HTTP v1 credentials loaded for project {}", projectId);
        }
    }
}
