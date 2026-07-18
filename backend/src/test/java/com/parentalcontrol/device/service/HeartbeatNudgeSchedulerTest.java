package com.parentalcontrol.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeartbeatNudgeSchedulerTest {

  @Mock DeviceDao deviceRepository;
  @Mock FcmWakeService fcmWakeService;

  HeartbeatNudgeScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new HeartbeatNudgeScheduler(deviceRepository, fcmWakeService);
  }

  /**
   * Reproduces the offline-flicker bug: on the burst-FGS path the only guaranteed heartbeat cadence
   * is WorkManager's 15-minute periodic floor, which is longer than the (pre-fix) 10-minute offline
   * cutoff. A device quiet for 6 minutes must be nudged well before it would ever be marked
   * OFFLINE.
   */
  @Test
  void nudgesDevicesQuietPastThreshold_beforeTheyWouldGoOffline() {
    Device quietDevice = deviceOnlineSince(6);
    when(deviceRepository.findByStatusAndLastSeenAtBefore(eq(DeviceStatus.ONLINE), any()))
        .thenReturn(List.of(quietDevice));

    scheduler.nudgeQuietDevices();

    verify(fcmWakeService, times(1)).wakeDevice(eq(quietDevice), eq("HEARTBEAT_NUDGE"), anyMap());
  }

  @Test
  void doesNotNudgeDevicesThatHeartbeatedRecently() {
    when(deviceRepository.findByStatusAndLastSeenAtBefore(eq(DeviceStatus.ONLINE), any()))
        .thenReturn(List.of());

    scheduler.nudgeQuietDevices();

    verify(fcmWakeService, never()).wakeDevice(any(), any(), anyMap());
  }

  @Test
  void queriesOnlyOnlineDevices_notAlreadyOfflineOrRevoked() {
    scheduler.nudgeQuietDevices();

    ArgumentCaptor<DeviceStatus> statusCaptor = ArgumentCaptor.forClass(DeviceStatus.class);
    verify(deviceRepository).findByStatusAndLastSeenAtBefore(statusCaptor.capture(), any());
    assertThat(statusCaptor.getValue()).isEqualTo(DeviceStatus.ONLINE);
  }

  private Device deviceOnlineSince(int minutesAgo) {
    Device device = new Device();
    device.setId(UUID.randomUUID());
    device.setStatus(DeviceStatus.ONLINE);
    device.setLastSeenAt(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
    return device;
  }
}
