package com.parentalcontrol.audit.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditLogResponse {
  private UUID id;
  private UUID userId;
  private UUID deviceId;
  private String action;
  private String resource;
  private String ipAddress;
  private String metadataJson;
  private Instant createdAt;
}
