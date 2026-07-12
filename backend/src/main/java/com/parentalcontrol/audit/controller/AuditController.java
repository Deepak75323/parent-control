package com.parentalcontrol.audit.controller;

import com.parentalcontrol.audit.dto.AuditLogResponse;
import com.parentalcontrol.audit.service.AuditQueryService;
import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.api.PageResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.AUDIT)
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Parent action audit trail")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

  private final AuditQueryService auditQueryService;

  @GetMapping
  @Operation(summary = "List audit logs for authenticated parent")
  public ApiResponse<PageResponse<AuditLogResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) UUID deviceId) {
    return ApiResponse.ok(auditQueryService.listForParent(page, size, deviceId));
  }
}
