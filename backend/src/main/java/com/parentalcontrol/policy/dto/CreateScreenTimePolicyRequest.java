package com.parentalcontrol.policy.dto;

import com.parentalcontrol.policy.domain.ScreenTimeAction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateScreenTimePolicyRequest {

    @NotBlank
    private String name;

    @NotEmpty
    private List<UUID> deviceIds;

    private String packageName;
    private String appCategory;

    @Min(1)
    private int dailyLimitMinutes;

    private ScreenTimeAction actionOnLimit = ScreenTimeAction.BLOCK_APP;
    private int warnAtPercent = 80;
    private Map<String, Object> weeklyScheduleJson;
}
