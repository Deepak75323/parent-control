package com.parentalcontrol.policy.dto;

import com.parentalcontrol.policy.domain.DeviceScheduleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateDeviceSchedulePolicyRequest {

    @NotBlank
    private String name;

    @NotEmpty
    private List<UUID> deviceIds;

    @NotNull
    private DeviceScheduleType scheduleType;

    @NotBlank
    private String startTime;

    @NotBlank
    private String endTime;

    private List<String> daysOfWeek = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    private boolean lockDevice;
}
