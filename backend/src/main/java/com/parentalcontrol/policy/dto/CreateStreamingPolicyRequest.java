package com.parentalcontrol.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateStreamingPolicyRequest {

    @NotBlank
    private String name;

    @NotEmpty
    private List<UUID> deviceIds;

    private boolean screenShareAllowed = true;
    private boolean cameraAllowed = true;
    private boolean audioAllowed = true;
}
