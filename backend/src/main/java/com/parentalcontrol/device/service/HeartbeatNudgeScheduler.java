package com.parentalcontrol.device.service;

import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the gap between the child's guaranteed heartbeat cadence and {@link
 * DevicePresenceScheduler}'s offline cutoff.
 *
 * <p>On Android 15+ the child app runs short dataSync FGS bursts instead of a 24/7 foreground
 * service (see MonitoringForegroundService / MonitoringPlatform.useBurstFgs()). In steady state,
 * absent any parent-initiated command, the only recurring trigger for a new burst is WorkManager's
 * periodic floor of 15 minutes (MonitoringWorkScheduler.PERIODIC_INTERVAL_MIN) — which is already
 * longer than DevicePresenceScheduler's 10-minute offline cutoff, before any Doze/OEM scheduling
 * delay is even considered. That mismatch guarantees an offline flicker on every idle cycle,
 * independent of network quality.
 *
 * <p>This scheduler finds devices that are still marked ONLINE but have gone quiet for longer than
 * {@link #NUDGE_AFTER_MINUTES}, and sends a high-priority FCM data wake so the child posts a fresh
 * heartbeat well before the offline cutoff — without needing a full monitoring burst or foreground
 * service promotion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatNudgeScheduler {

  /** Must stay below DevicePresenceScheduler.OFFLINE_THRESHOLD_MINUTES with margin. */
  private static final long NUDGE_AFTER_MINUTES = 5;

  private final DeviceDao deviceRepository;
  private final FcmWakeService fcmWakeService;

  @Scheduled(fixedRate = 120_000)
  @Transactional(readOnly = true)
  public void nudgeQuietDevices() {
    Instant cutoff = Instant.now().minus(NUDGE_AFTER_MINUTES, ChronoUnit.MINUTES);
    List<Device> quiet =
        deviceRepository.findByStatusAndLastSeenAtBefore(DeviceStatus.ONLINE, cutoff);
    for (Device device : quiet) {
      log.info(
          "HEARTBEAT_NUDGE sending device={} lastSeenAt={} quietForMin>={}",
          device.getId(),
          device.getLastSeenAt(),
          NUDGE_AFTER_MINUTES);
      fcmWakeService.wakeDevice(device, "HEARTBEAT_NUDGE", Map.of());
    }
  }
}
