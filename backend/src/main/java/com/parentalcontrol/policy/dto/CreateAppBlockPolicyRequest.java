package com.parentalcontrol.policy.dto;

import com.parentalcontrol.policy.domain.ScheduleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateAppBlockPolicyRequest {

  @NotBlank private String name;

  @NotEmpty private List<UUID> deviceIds;

  @NotEmpty private List<AppBlockEntry> rules;

  @Data
  public static class AppBlockEntry {
    @NotBlank private String packageName;
    private ScheduleType scheduleType = ScheduleType.ALWAYS;
    private Map<String, Object> scheduleJson;
  }
}
