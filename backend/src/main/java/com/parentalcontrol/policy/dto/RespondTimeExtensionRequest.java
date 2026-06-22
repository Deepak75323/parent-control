package com.parentalcontrol.policy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RespondTimeExtensionRequest {

    private boolean approved;

    @Min(5)
    @Max(240)
    private int grantMinutes = 30;
}
