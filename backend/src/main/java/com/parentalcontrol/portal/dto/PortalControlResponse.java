package com.parentalcontrol.portal.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalControlResponse {
  private boolean disabled;
  private boolean canControl;
  private boolean canShare;
  private List<PortalOperatorDto> operators;
}
