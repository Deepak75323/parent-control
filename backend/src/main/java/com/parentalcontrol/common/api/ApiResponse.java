package com.parentalcontrol.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.observability.CorrelationContext;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private boolean success;
  private String message;

  /** Machine-readable error code (e.g. REPAIR_REQUIRED) when success=false. */
  private String code;

  /** Request correlation ID — include in support requests. */
  private String correlationId;

  private T data;
  @Builder.Default private Instant timestamp = Instant.now();

  public static <T> ApiResponse<T> ok(T data) {
    return ApiResponse.<T>builder()
        .success(true)
        .correlationId(CorrelationContext.correlationId())
        .data(data)
        .build();
  }

  public static <T> ApiResponse<T> ok(String message, T data) {
    return ApiResponse.<T>builder()
        .success(true)
        .message(message)
        .correlationId(CorrelationContext.correlationId())
        .data(data)
        .build();
  }

  public static ApiResponse<Void> okMessage(String message) {
    return ApiResponse.<Void>builder()
        .success(true)
        .message(message)
        .correlationId(CorrelationContext.correlationId())
        .build();
  }

  public static ApiResponse<Void> error(String message) {
    return ApiResponse.<Void>builder()
        .success(false)
        .message(message)
        .correlationId(CorrelationContext.correlationId())
        .build();
  }

  public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
    return ApiResponse.<Void>builder()
        .success(false)
        .code(errorCode.getCode())
        .message(message)
        .correlationId(CorrelationContext.correlationId())
        .build();
  }
}
