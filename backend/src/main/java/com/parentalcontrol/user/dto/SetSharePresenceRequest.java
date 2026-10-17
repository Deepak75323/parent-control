package com.parentalcontrol.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetSharePresenceRequest {
  @NotNull private Boolean enabled;
}
