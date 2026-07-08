package com.parentalcontrol.user.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DefaultChildLoginPasswordStatusResponse {
    boolean configured;
    int linkedDeviceCount;
}
