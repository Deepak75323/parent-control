package com.parentalcontrol.device.dto;

import lombok.Data;

@Data
public class TamperVerifyRequest {
    /** Parent password / PIN configured in the parent app. */
    private String pin;
    /** One-time verification code from parent Alerts. */
    private String code;
    /** {@code accessibility} or {@code device_admin} — used for audit logging. */
    private String target;
}
