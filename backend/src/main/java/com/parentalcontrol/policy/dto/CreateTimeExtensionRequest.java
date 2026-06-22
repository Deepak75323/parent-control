package com.parentalcontrol.policy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTimeExtensionRequest {

    @NotBlank
    private String packageName;

    private String appLabel;

    @Min(5)
    @Max(240)
    private int requestedMinutes = 30;
}
