package com.parentalcontrol.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.audit.domain.AuditLog;
import com.parentalcontrol.audit.repository.AuditLogRepository;
import com.parentalcontrol.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void log(String action, String resource, UUID deviceId, Map<String, Object> metadata) {
        UUID userId = SecurityUtils.currentUser().map(u -> u.getId()).orElse(null);
        String ip = resolveClientIp();

        String metadataJson = null;
        if (metadata != null && !metadata.isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException ignored) {
                metadataJson = metadata.toString();
            }
        }

        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .deviceId(deviceId)
                .action(action)
                .resource(resource)
                .ipAddress(ip)
                .metadataJson(metadataJson)
                .build());
    }

    private static String resolveClientIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
