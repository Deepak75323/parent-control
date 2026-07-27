package com.parentalcontrol.device.service;

import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import com.parentalcontrol.monitoring.service.StreamingSessionCleanupService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DevicePresenceScheduler {

  /**
   * Must stay comfortably above HeartbeatNudgeScheduler.NUDGE_AFTER_MINUTES (5) plus realistic
   * FCM/Doze/OEM delivery jitter, so a nudge has time to land before a device flips OFFLINE.
   * Reduced to 8 minutes for faster offline detection with network connectivity monitoring.
   */
  private static final long OFFLINE_THRESHOLD_MINUTES = 8;

  private final DeviceDao deviceRepository;
  private final RealtimeEventService realtimeEventService;
  private final StreamingSessionCleanupService streamingSessionCleanupService;

  @Scheduled(fixedRate = 60_000)
  @Transactional
  public void markStaleDevicesOffline() {
    Instant cutoff = Instant.now().minus(OFFLINE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
    List<Device> stale =
        deviceRepository.findByStatusAndLastSeenAtBefore(DeviceStatus.ONLINE, cutoff);
    for (Device device : stale) {
      device.setStatus(DeviceStatus.OFFLINE);
      deviceRepository.save(device);
      
      // Clean up any active streaming sessions for this offline device
      streamingSessionCleanupService.cleanupSessionsForOfflineDevice(device.getId());
      
      realtimeEventService.publishDeviceStatus(device);
      log.info("Marked device {} OFFLINE — lastSeenAt={}, cleaned up streaming sessions", 
          device.getId(), device.getLastSeenAt());
    }
  }
}
