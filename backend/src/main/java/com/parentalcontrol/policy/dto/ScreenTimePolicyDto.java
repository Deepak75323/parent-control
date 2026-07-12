package com.parentalcontrol.policy.dto;

import com.parentalcontrol.policy.domain.ScreenTimeAction;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenTimePolicyDto {

  private UUID policyId;
  private UUID ruleId;
  private String packageName;
  private String appCategory;
  private int dailyLimitMinutes;
  private ScreenTimeAction actionOnLimit;
  private int warnAtPercent;
  private Map<String, Object> weeklyScheduleJson;
  private boolean enabled;
}
