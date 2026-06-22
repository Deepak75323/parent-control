package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InstalledAppItemRequest {

    @NotBlank
    private String packageName;

    @NotBlank
    private String appLabel;

    private String appCategory;
}
