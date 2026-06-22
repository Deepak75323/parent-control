package com.parentalcontrol.policy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PolicyAckRequest {

    @NotBlank
    private String policyVersion;
}
