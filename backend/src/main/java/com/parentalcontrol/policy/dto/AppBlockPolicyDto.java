package com.parentalcontrol.policy.dto;

import com.parentalcontrol.policy.domain.ScheduleType;
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
public class AppBlockPolicyDto {

  private UUID policyId;
  private UUID ruleId;
  private String packageName;
  private ScheduleType scheduleType;
  private Map<String, Object> scheduleJson;
  private boolean enabled;
}
