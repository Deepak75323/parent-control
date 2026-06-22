package com.parentalcontrol.audit.service;

import com.parentalcontrol.audit.domain.AuditLog;
import com.parentalcontrol.audit.dto.AuditLogResponse;
import com.parentalcontrol.audit.repository.AuditLogRepository;
import com.parentalcontrol.common.api.PageResponse;
import com.parentalcontrol.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> listForParent(int page, int size, UUID deviceId) {
        UUID userId = SecurityUtils.requireUserId();
        Page<AuditLog> result = deviceId == null
                ? auditLogRepository.findForParent(userId, PageRequest.of(page, size))
                : auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId, PageRequest.of(page, size));

        var mapped = result.map(this::toResponse);
        return PageResponse.of(mapped.getContent(), page, size, mapped.getTotalElements());
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
