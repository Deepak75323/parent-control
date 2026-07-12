package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class StartScreenShareRequest {

  @NotNull private UUID deviceId;
}
