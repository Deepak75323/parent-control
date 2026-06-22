package com.parentalcontrol.device.event;

import java.util.UUID;

/**
 * Published when a parent command row is saved. STOMP/FCM delivery is handled
 * after the surrounding transaction commits so child poll/STOMP never races an uncommitted row.
 */
public record DeviceCommandCreatedEvent(
        UUID deviceId,
        UUID commandId,
        String commandType
) {
}
