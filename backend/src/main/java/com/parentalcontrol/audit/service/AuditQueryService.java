package com.parentalcontrol.audit.service;

import com.parentalcontrol.audit.dao.AuditLogDao;
import com.parentalcontrol.audit.domain.AuditLog;
import com.parentalcontrol.audit.dto.AuditLogResponse;
import com.parentalcontrol.common.api.PageResponse;
import com.parentalcontrol.common.constant.TimeoutConstants;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.monitoring.service.DeviceAccessService;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

  private static final int MAX_PAGE_SIZE = TimeoutConstants.MAX_PAGE_SIZE;

  private final AuditLogDao auditLogRepository;
  private final DeviceAccessService deviceAccessService;

  @Transactional(readOnly = true)
  public PageResponse<AuditLogResponse> listForParent(int page, int size, UUID deviceId) {
    UUID userId = SecurityUtils.requireUserId();
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    Page<AuditLog> result;
    if (Objects.isNull(deviceId)) {
      result = auditLogRepository.findForParent(userId, PageRequest.of(page, safeSize));
    } else {
      deviceAccessService.requireOwnedDevice(deviceId);
      result =
          auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(
              deviceId, PageRequest.of(page, safeSize));
    }
    var mapped = result.map(this::toResponse);
    return PageResponse.of(mapped.getContent(), page, safeSize, mapped.getTotalElements());
  }

  private AuditLogResponse toResponse(AuditLog log) {
    return AuditLogResponse.builder()
        .id(log.getId())
        .userId(log.getUserId())
        .deviceId(log.getDeviceId())
        .action(log.getAction())
        .resource(log.getResource())
        .ipAddress(log.getIpAddress())
        .metadataJson(log.getMetadataJson())
        .createdAt(log.getCreatedAt())
        .build();
  }
}
