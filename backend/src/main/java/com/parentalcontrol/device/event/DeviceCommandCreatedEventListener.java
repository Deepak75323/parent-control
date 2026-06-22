package com.parentalcontrol.device.event;

import com.parentalcontrol.device.repository.DeviceRepository;
import com.parentalcontrol.device.service.FcmWakeService;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceCommandCreatedEventListener {

    private final RealtimeEventService realtimeEventService;
    private final FcmWakeService fcmWakeService;
    private final DeviceRepository deviceRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeviceCommandCreated(DeviceCommandCreatedEvent event) {
        realtimeEventService.publishDeviceCommand(
                event.deviceId(),
                event.commandId(),
                event.commandType());

        deviceRepository.findById(event.deviceId()).ifPresentOrElse(
                device -> fcmWakeService.wakeDevice(
                        device,
                        "DEVICE_COMMAND",
                        Map.of(
                                "commandId", event.commandId().toString(),
                                "commandType", event.commandType())),
                () -> log.warn(
                        "Device {} not found for FCM wake after command {}",
                        event.deviceId(),
                        event.commandId()));
    }
}
