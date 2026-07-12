package com.parentalcontrol.policy.dto;

import com.parentalcontrol.policy.domain.DeviceScheduleType;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSchedulePolicyDto {

  private UUID policyId;
  private UUID ruleId;
  private DeviceScheduleType scheduleType;
  private String startTime;
  private String endTime;
  private List<String> daysOfWeek;
  private boolean lockDevice;
  private boolean enabled;
}
