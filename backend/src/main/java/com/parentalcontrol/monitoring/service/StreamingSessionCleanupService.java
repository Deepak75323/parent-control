package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.monitoring.dao.ScreenSessionDao;
import com.parentalcontrol.monitoring.domain.ScreenSession;
import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import com.parentalcontrol.user.dao.ParentProfileDao;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for cleaning up streaming sessions when devices go offline or when network
 * connectivity is lost. Ensures sessions don't remain in PENDING or ACTIVE state when the child
 * device is unreachable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingSessionCleanupService {

  private final ScreenSessionDao screenSessionRepository;
  private final RealtimeEventService realtimeEventService;
  private final ParentProfileDao parentProfileRepository;
  private final DeviceDao deviceRepository;
  private final SignalingCleanupScheduler signalingCleanupScheduler;

  /**
   * Clean up all active streaming sessions for a device that has gone offline. This prevents
   * sessions from being stuck in PENDING or ACTIVE state when the child device is unreachable due
   * to network issues.
   */
  @Transactional
  public void cleanupSessionsForOfflineDevice(UUID deviceId) {
    List<ScreenSession> activeSessions =
        screenSessionRepository.findActiveSessionsByDeviceId(deviceId);

    if (activeSessions.isEmpty()) {
      log.debug("No active streaming sessions found for offline device {}", deviceId);
      return;
    }

    log.info(
        "Cleaning up {} active streaming sessions for offline device {}",
        activeSessions.size(),
        deviceId);

    for (ScreenSession session : activeSessions) {
      cleanupSession(session, "Child device went offline");
    }
  }

  /**
   * Clean up sessions that have been pending for too long without any child response. This handles
   * cases where the child received a stream request but never responded due to network issues or
   * app crashes.
   */
  @Transactional
  public void cleanupStalePendingSessions(long maxPendingMinutes) {
    Instant cutoff = Instant.now().minusSeconds(maxPendingMinutes * 60);
    List<ScreenSession> staleSessions =
        screenSessionRepository.findByStatusAndStartedAtBefore(ScreenSessionStatus.PENDING, cutoff);

    if (staleSessions.isEmpty()) {
      return;
    }

    log.info(
        "Cleaning up {} stale pending streaming sessions older than {} minutes",
        staleSessions.size(),
        maxPendingMinutes);

    for (ScreenSession session : staleSessions) {
      cleanupSession(session, "Session timed out waiting for child response");
    }
  }

  /**
   * Force cleanup a specific session by ID. Used when network connectivity is detected to be lost
   * during an active session.
   */
  @Transactional
  public void forceCleanupSession(UUID sessionId, String reason) {
    screenSessionRepository
        .findById(sessionId)
        .ifPresent(
            session -> {
              if (session.getStatus() == ScreenSessionStatus.PENDING
                  || session.getStatus() == ScreenSessionStatus.ACTIVE) {
                log.info("Force cleaning up streaming session {} due to: {}", sessionId, reason);
                cleanupSession(session, reason);
              }
            });
  }

  /** Internal method to perform the actual session cleanup logic. */
  private void cleanupSession(ScreenSession session, String reason) {
    try {
      // Mark session as failed
      session.setStatus(ScreenSessionStatus.FAILED);
      session.setEndedAt(Instant.now());
      screenSessionRepository.save(session);

      // Clean up signaling room
      if (session.getSignalingRoomId() != null) {
        signalingCleanupScheduler.purgeRoom(session.getSignalingRoomId());
      }

      // Notify parent about session failure
      deviceRepository
          .findById(session.getDeviceId())
          .ifPresent(
              device -> {
                parentProfileRepository
                    .findUserIdByProfileId(device.getParentProfileId())
                    .ifPresent(
                        parentUserId -> {
                          realtimeEventService.publishStreamingState(
                              parentUserId,
                              session.getDeviceId(),
                              session.getSignalingRoomId(),
                              "FAILED",
                              session.getStreamType(),
                              reason);
                        });
              });

      // Send device stream stop event
      realtimeEventService.publishDeviceStreamStop(
          session.getDeviceId(), session.getId(), session.getSignalingRoomId());

      log.debug("Successfully cleaned up streaming session {}: {}", session.getId(), reason);
    } catch (Exception e) {
      log.error("Failed to cleanup streaming session {}: {}", session.getId(), e.getMessage(), e);
    }
  }

  /**
   * Scheduled task to clean up stale pending sessions every 5 minutes. Sessions that have been
   * pending for more than 10 minutes are considered stale.
   */
  @Scheduled(fixedRate = 300_000) // 5 minutes
  @Transactional
  public void scheduledStalePendingCleanup() {
    cleanupStalePendingSessions(10); // 10 minutes timeout
  }
}
