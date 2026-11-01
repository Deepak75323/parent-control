package com.parentalcontrol.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.audit.dao.AuditLogDao;
import com.parentalcontrol.audit.domain.AuditLog;
import com.parentalcontrol.common.security.AuthenticatedUser;
import com.parentalcontrol.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditService {

  private static final int USER_AGENT_MAX_LEN = 512;

  private final AuditLogDao auditLogRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public void log(String action, String resource, UUID deviceId, Map<String, Object> metadata) {
    UUID userId = SecurityUtils.currentUser().map(AuthenticatedUser::getId).orElse(null);
    save(userId, action, resource, deviceId, metadata);
  }

  /**
   * Like {@link #log}, but sets {@code userId} explicitly (needed for login before the security
   * context has a principal).
   */
  @Transactional
  public void logForUser(
      UUID userId, String action, String resource, UUID deviceId, Map<String, Object> metadata) {
    save(userId, action, resource, deviceId, metadata);
  }

  private void save(
      UUID userId, String action, String resource, UUID deviceId, Map<String, Object> metadata) {
    String ip = resolveClientIp();
    String metadataJson = toJson(metadata);
    auditLogRepository.save(
        AuditLog.builder()
            .userId(userId)
            .deviceId(deviceId)
            .action(action)
            .resource(resource)
            .ipAddress(ip)
            .metadataJson(metadataJson)
            .build());
  }

  /** Truncated {@code User-Agent} from the current HTTP request, or null. */
  public static String resolveUserAgent() {
    try {
      var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs == null) {
        return null;
      }
      String ua = attrs.getRequest().getHeader("User-Agent");
      if (ua == null || ua.isBlank()) {
        return null;
      }
      String trimmed = ua.trim();
      return trimmed.length() <= USER_AGENT_MAX_LEN
          ? trimmed
          : trimmed.substring(0, USER_AGENT_MAX_LEN);
    } catch (Exception e) {
      return null;
    }
  }

  private String toJson(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (JsonProcessingException ignored) {
      return metadata.toString();
    }
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
