package com.parentalcontrol.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalingMessageResponse {

    private UUID id;
    private String roomId;
    private String messageType;
    private String payload;
    private String senderRole;
}
