package com.parentalcontrol.portal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetPortalDisabledRequest {
  @NotNull private Boolean disabled;
}
