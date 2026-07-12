package com.parentalcontrol.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateGeofencePolicyRequest {

  @NotBlank private String name;

  @NotEmpty private List<UUID> deviceIds;

  @NotBlank private String geofenceName;

  @NotNull private Double latitude;

  @NotNull private Double longitude;

  @Positive private double radiusM;

  private boolean alertOnEnter = true;
  private boolean alertOnExit = true;
}
