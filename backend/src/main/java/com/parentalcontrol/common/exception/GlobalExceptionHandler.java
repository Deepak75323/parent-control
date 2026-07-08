package com.parentalcontrol.common.exception;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.observability.SecurityObservabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final SecurityObservabilityService securityObservabilityService;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        recordSecurityMetric(ex);
        return ResponseEntity
                .status(ex.getErrorCode().getStatus())
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(details));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials() {
        return ResponseEntity
                .status(ErrorCode.INVALID_CREDENTIALS.getStatus())
                .body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        if (message != null && message.toLowerCase().contains("pairing_codes")) {
            return ResponseEntity
                    .status(ErrorCode.CONFLICT.getStatus())
                    .body(ApiResponse.error("PAIRING_CODE_COLLISION"));
        }
        return ResponseEntity
                .status(ErrorCode.USER_EXISTS.getStatus())
                .body(ApiResponse.error("Resource already exists"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.error("An unexpected error occurred"));
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
            default -> { }
        }
    }
}
