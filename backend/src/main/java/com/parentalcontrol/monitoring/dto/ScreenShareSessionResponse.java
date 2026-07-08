package com.parentalcontrol.monitoring.dto;

import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ScreenShareSessionResponse {

    private UUID sessionId;
    private UUID deviceId;
    private String signalingRoomId;
    private String streamType;
    private ScreenSessionStatus status;
    private Instant startedAt;
    private String message;
    /** NATIVE = silent Device Owner capture; RN_DISPLAY_MEDIA = standard consent dialog */
    private String captureMode;
    /** End-to-end trace id for stream debugging */
    private String traceId;
}
