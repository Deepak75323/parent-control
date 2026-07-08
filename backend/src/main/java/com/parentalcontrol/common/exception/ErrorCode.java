package com.parentalcontrol.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND"),
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT"),
    PAIRING_CODE_INVALID(HttpStatus.BAD_REQUEST, "PAIRING_CODE_INVALID"),
    PAIRING_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "PAIRING_CODE_EXPIRED"),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND"),
    USER_EXISTS(HttpStatus.CONFLICT, "USER_EXISTS"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS"),
    DEVICE_TRUST_DENIED(HttpStatus.FORBIDDEN, "DEVICE_TRUST_DENIED"),
    REPAIR_REQUIRED(HttpStatus.FORBIDDEN, "REPAIR_REQUIRED"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");

    private final HttpStatus status;
    private final String code;

    ErrorCode(HttpStatus status, String code) {
        this.status = status;
        this.code = code;
    }
}
