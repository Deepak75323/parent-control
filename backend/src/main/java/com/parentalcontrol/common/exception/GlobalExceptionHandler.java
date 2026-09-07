package com.parentalcontrol.common.exception;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.observability.SecurityObservabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

  private final SecurityObservabilityService securityObservabilityService;

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
    recordSecurityMetric(ex);
    return ResponseEntity.status(ex.getErrorCode().getStatus())
        .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    log.warn(
        "Validation failed correlationId={} fields={}",
        correlationId(),
        ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + "=" + err.getDefaultMessage())
            .toList());
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Invalid request data"));
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiResponse<Void>> handleBadCredentials() {
    return ResponseEntity.status(ErrorCode.INVALID_CREDENTIALS.getStatus())
        .body(ApiResponse.error(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
    log.warn("Missing multipart part correlationId={} part={}", correlationId(), ex.getRequestPartName());
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_ATTACHMENT_REQUIRED));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
    log.warn("Upload too large correlationId={}", correlationId());
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
        .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_ATTACHMENT_TOO_LARGE));
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
    log.warn("Multipart request failed correlationId={}", correlationId(), ex);
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_ATTACHMENT_REQUIRED));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException ex) {
    log.warn(
        "Unsupported media type correlationId={} contentType={}",
        correlationId(),
        ex.getContentType());
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_ATTACHMENT_REQUIRED));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDuplicate(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation correlationId={}", correlationId(), ex);
    return ResponseEntity.status(ErrorCode.CONFLICT.getStatus())
        .body(ApiResponse.error(ErrorCode.CONFLICT, "Resource already exists"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
    log.error("Unhandled exception correlationId={}", correlationId(), ex);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
        .body(
            ApiResponse.error(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Reference the correlation ID when contacting support."));
  }

  private static String correlationId() {
    return com.parentalcontrol.observability.CorrelationContext.correlationId();
  }

  private void recordSecurityMetric(BusinessException ex) {
    switch (ex.getErrorCode()) {
      case PAIRING_CODE_INVALID, PAIRING_CODE_EXPIRED ->
          securityObservabilityService.recordPairingFailure(ex.getErrorCode().getCode());
      case DEVICE_TRUST_DENIED, UNAUTHORIZED -> {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("integrity") || msg.contains("play")) {
          securityObservabilityService.recordIntegrityFailure(ex.getErrorCode().getCode());
        } else if (msg.contains("pair")) {
          securityObservabilityService.recordPairingFailure(ex.getErrorCode().getCode());
        }
      }
      default -> {}
    }
  }
}
