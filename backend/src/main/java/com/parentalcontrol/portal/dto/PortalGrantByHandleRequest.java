package com.parentalcontrol.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalGrantByHandleRequest {
  @NotBlank private String familyHandle;
  @NotBlank private String password;
}
