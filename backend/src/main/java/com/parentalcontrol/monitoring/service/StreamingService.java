package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.constant.AppConstants;
import com.parentalcontrol.common.constant.AuditActionConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.service.DevicePresenceScheduler;
import com.parentalcontrol.monitoring.dao.ScreenCaptureAuditLogDao;
import com.parentalcontrol.monitoring.dao.ScreenSessionDao;
import com.parentalcontrol.monitoring.domain.ScreenCaptureAuditLog;
import com.parentalcontrol.monitoring.domain.ScreenSession;
import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import com.parentalcontrol.monitoring.dto.ScreenShareSessionResponse;
import com.parentalcontrol.monitoring.dto.StartScreenShareRequest;
import com.parentalcontrol.monitoring.dto.StartStreamRequest;
import com.parentalcontrol.observability.CorrelationContext;
import com.parentalcontrol.observability.StreamFunnelStage;
import com.parentalcontrol.observability.StreamObservabilityService;
import com.parentalcontrol.observability.StreamState;
import com.parentalcontrol.observability.StreamTraceLogger;
import com.parentalcontrol.policy.dao.PolicyAssignmentDao;
import com.parentalcontrol.policy.dao.PolicyDao;
import com.parentalcontrol.policy.dao.StreamingPolicyRuleDao;
import com.parentalcontrol.policy.domain.PolicyType;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.service.ParentPinService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class StreamingService {

  private final ScreenSessionDao screenSessionRepository;
  private final DeviceAccessService deviceAccessService;
  private final RealtimeEventService realtimeEventService;
  private final ParentProfileDao parentProfileRepository;
  private final PolicyAssignmentDao policyAssignmentRepository;
  private final PolicyDao policyRepository;
  private final StreamingPolicyRuleDao streamingPolicyRuleRepository;
  private final ParentPinService parentPinService;
  private final ScreenCaptureAuditLogDao screenCaptureAuditLogDao;
  private final AuditService auditService;
  private final FeatureGateService featureGateService;
  private final SignalingCleanupScheduler signalingCleanupScheduler;
  private final com.parentalcontrol.device.service.FcmWakeService fcmWakeService;
  private final StreamObservabilityService streamObservabilityService;

  @Transactional
  public ScreenShareSessionResponse startScreenShare(StartScreenShareRequest request) {
    return startStream(request.getDeviceId(), AppConstants.STREAM_TYPE_SCREEN);
  }

  @Transactional
  public ScreenShareSessionResponse startStream(StartStreamRequest request) {
    return startStream(
        request.getDeviceId(),
        request.getStreamType().toUpperCase(),
        request.getParentPin(),
        request.getCameraFacing());
  }

  @Transactional
  public ScreenShareSessionResponse startStream(UUID deviceId, String streamType) {
    return startStream(deviceId, streamType, null, null);
  }

  @Transactional
  public ScreenShareSessionResponse startStream(
      UUID deviceId, String streamType, String parentPin) {
    return startStream(deviceId, streamType, parentPin, null);
  }

  @Transactional
  public ScreenShareSessionResponse startStream(
      UUID deviceId, String streamType, String parentPin, String cameraFacing) {
    String traceId = StreamTraceLogger.resolveOrCreateTraceId();
    StreamTraceLogger.transitionState(
        "Backend",
        StreamState.REQUEST_SENT,
        "startStream",
        Map.of("deviceId", deviceId, "streamType", streamType, "traceId", traceId));
    StreamTraceLogger.info(
        "Backend",
        "START",
        "startStream",
        "incoming API",
        Map.of("deviceId", deviceId, "streamType", streamType, "traceId", traceId));

    Device device = deviceAccessService.requireOwnedDevice(deviceId);
    deviceAccessService.requireMonitoringActive(device);

    // Check if device is online before starting stream session
    assertDeviceOnline(device, streamType);

    StreamTraceLogger.info(
        "Backend",
        "LOOKUP",
        "startStream",
        "device lookup success",
        Map.of(
            "deviceId",
            device.getId(),
            "fcmTokenPresent",
            device.getFcmToken() != null && !device.getFcmToken().isBlank(),
            "deviceStatus",
            device.getStatus().name()));
    featureGateService.requireLiveStreaming(device.getParentProfileId());
    assertStreamingAllowed(device.getId(), streamType);
    assertScreenCaptureAvailable(device, streamType);
    endStalePendingSessions(device.getId(), streamType);

    String normalizedFacing = normalizeCameraFacing(cameraFacing, streamType);
    String captureMode = resolveCaptureMode(device, streamType);
    if ("NATIVE".equals(captureMode)) {
      parentPinService.requireSilentStreamAccess(parentPin);
    }

    ScreenSession session =
        screenSessionRepository.save(
            ScreenSession.builder()
                .deviceId(device.getId())
                .startedAt(Instant.now())
                .status(ScreenSessionStatus.PENDING)
                .streamType(streamType)
                .build());

    CorrelationContext.setSessionId(session.getId());
    streamObservabilityService.recordStreamStart(streamType);
    streamObservabilityService.recordFunnelStage(StreamFunnelStage.REQUEST_SENT, streamType);

    String roomId = streamType.toLowerCase() + "_" + device.getId() + "_" + session.getId();
    session.setSignalingRoomId(roomId);
    final ScreenSession activeSession = screenSessionRepository.save(session);
    final UUID sessionId = activeSession.getId();
    final UUID notifyDeviceId = device.getId();
    final UUID parentProfileId = device.getParentProfileId();
    final String notifyStreamType = streamType;
    final String notifyFacing = normalizedFacing;
    final String notifyTraceId = traceId;

    // Notify child only after commit — STOMP/FCM can wake the child in <200 ms while this
    // transaction is still open; signaling then returns 404 "Streaming session not found".
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            parentProfileRepository
                .findUserIdByProfileId(parentProfileId)
                .ifPresent(
                    parentUserId ->
                        realtimeEventService.publishStreamingState(
                            parentUserId,
                            notifyDeviceId,
                            roomId,
                            "PENDING",
                            notifyStreamType,
                            null,
                            notifyTraceId));
            realtimeEventService.publishDeviceStreamRequest(
                notifyDeviceId, roomId, notifyStreamType, sessionId, notifyFacing, notifyTraceId);
            Map<String, String> wakeData = new HashMap<>();
            wakeData.put("roomId", roomId);
            wakeData.put("streamType", notifyStreamType);
            wakeData.put("sessionId", sessionId.toString());
            wakeData.put("traceId", notifyTraceId);
            if (notifyFacing != null) {
              wakeData.put("cameraFacing", notifyFacing);
            }
            fcmWakeService.wakeDevice(device, "STREAM_REQUEST", wakeData);
            // wakeDevice no-ops when FCM is disabled — do not claim a wake was sent.
            StreamTraceLogger.info(
                "Backend",
                "NOTIFY",
                "startStream",
                "FCM wake requested (no-op if FCM_ENABLED=false)",
                Map.of(
                    "deviceId",
                    notifyDeviceId,
                    "action",
                    "STREAM_REQUEST",
                    "traceId",
                    notifyTraceId));
            StreamTraceLogger.info(
                "Backend",
                "SOCKET",
                "startStream",
                "STOMP stream-request emitted",
                Map.of("roomId", roomId, "sessionId", sessionId, "traceId", notifyTraceId));
            StreamTraceLogger.transitionState(
                "Backend",
                StreamState.REQUEST_RECEIVED,
                "startStream",
                Map.of("roomId", roomId, "sessionId", sessionId));
          }
        });

    UUID parentUserId = SecurityUtils.currentUser().map(u -> u.getId()).orElse(null);
    screenCaptureAuditLogDao.save(
        ScreenCaptureAuditLog.builder()
            .deviceId(device.getId())
            .sessionId(sessionId)
            .streamType(streamType)
            .captureMode(captureMode)
            .parentUserId(parentUserId)
            .startedAt(Instant.now())
            .build());

    auditService.log(
        AuditActionConstants.STREAM_START,
        "session:" + sessionId,
        device.getId(),
        Map.of("streamType", streamType, "captureMode", captureMode, "roomId", roomId));

    return ScreenShareSessionResponse.builder()
        .sessionId(activeSession.getId())
        .deviceId(device.getId())
        .signalingRoomId(roomId)
        .streamType(streamType)
        .status(activeSession.getStatus())
        .startedAt(activeSession.getStartedAt())
        .captureMode(captureMode)
        .traceId(traceId)
        .message("WebRTC signaling room ready. Connect via /api/v1/monitoring/signaling/" + roomId)
        .build();
  }

  @Transactional
  public ScreenShareSessionResponse stopScreenShare(UUID sessionId) {
    StreamTraceLogger.transitionState(
        "Backend", StreamState.STOPPING, "stopScreenShare", Map.of("sessionId", sessionId));
    ScreenSession session =
        screenSessionRepository
            .findById(sessionId)
            .orElseThrow(
                () ->
                    new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.SESSION_NOT_FOUND));

    deviceAccessService.requireOwnedDevice(session.getDeviceId());

    session.setStatus(ScreenSessionStatus.ENDED);
    session.setEndedAt(Instant.now());
    final ScreenSession endedSession = screenSessionRepository.save(session);

    screenCaptureAuditLogDao
        .findFirstBySessionIdAndEndedAtIsNull(sessionId)
        .ifPresent(
            a -> {
              a.setEndedAt(Instant.now());
              screenCaptureAuditLogDao.save(a);
            });

    auditService.log(
        AuditActionConstants.STREAM_STOP,
        "session:" + sessionId,
        endedSession.getDeviceId(),
        Map.of("roomId", endedSession.getSignalingRoomId()));

    parentProfileRepository
        .findUserIdByProfileId(
            deviceAccessService.requireOwnedDevice(endedSession.getDeviceId()).getParentProfileId())
        .ifPresent(
            parentUserId ->
                realtimeEventService.publishStreamingState(
                    parentUserId,
                    endedSession.getDeviceId(),
                    endedSession.getSignalingRoomId(),
                    "ENDED",
                    endedSession.getStreamType()));

    realtimeEventService.publishDeviceStreamStop(
        endedSession.getDeviceId(), endedSession.getId(), endedSession.getSignalingRoomId());

    signalingCleanupScheduler.purgeRoom(endedSession.getSignalingRoomId());

    StreamTraceLogger.transitionState(
        "Backend", StreamState.STOPPED, "stopScreenShare", Map.of("sessionId", sessionId));

    return ScreenShareSessionResponse.builder()
        .sessionId(endedSession.getId())
        .deviceId(endedSession.getDeviceId())
        .signalingRoomId(endedSession.getSignalingRoomId())
        .streamType(endedSession.getStreamType())
        .status(endedSession.getStatus())
        .startedAt(endedSession.getStartedAt())
        .message("Stream session ended")
        .build();
  }

  @Transactional(readOnly = true)
  public List<ScreenShareSessionResponse> pendingSessionsForDevice() {
    Device device = deviceAccessService.requireAuthenticatedDevice();
    return screenSessionRepository
        .findByDeviceIdAndStatusOrderByStartedAtDesc(device.getId(), ScreenSessionStatus.PENDING)
        .stream()
        .map(this::toSessionResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<ScreenShareSessionResponse> activeSessionForDevice() {
    Device device = deviceAccessService.requireAuthenticatedDevice();
    return screenSessionRepository
        .findFirstByDeviceIdAndStatusOrderByStartedAtDesc(
            device.getId(), ScreenSessionStatus.ACTIVE)
        .map(this::toSessionResponse);
  }

  @Transactional
  public void switchCamera(UUID sessionId) {
    ScreenSession session =
        screenSessionRepository
            .findById(sessionId)
            .orElseThrow(
                () ->
                    new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.SESSION_NOT_FOUND));
    Device device = deviceAccessService.requireOwnedDevice(session.getDeviceId());
    if (!AppConstants.STREAM_TYPE_CAMERA.equalsIgnoreCase(session.getStreamType())) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          MessageConstants.CAMERA_SWITCH_ONLY_APPLIES_TO_CAMERA_STREAMS);
    }
    if (session.getStatus() != ScreenSessionStatus.ACTIVE
        && session.getStatus() != ScreenSessionStatus.PENDING) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.NO_ACTIVE_CAMERA_STREAM);
    }
    realtimeEventService.publishDeviceStreamControl(
        session.getDeviceId(), session.getId(), "SWITCH_CAMERA");
    Map<String, String> wakeData = new HashMap<>();
    wakeData.put("sessionId", session.getId().toString());
    wakeData.put("command", "SWITCH_CAMERA");
    fcmWakeService.wakeDevice(device, "SWITCH_CAMERA", wakeData);
    StreamTraceLogger.info(
        "Backend",
        "NOTIFY",
        "switchCamera",
        "SWITCH_CAMERA STOMP + FCM wake requested",
        Map.of("sessionId", sessionId, "deviceId", session.getDeviceId()));
  }

  @Transactional
  public void reportStreamFailure(UUID sessionId, String reason) {
    ScreenSession session =
        screenSessionRepository
            .findById(sessionId)
            .orElseThrow(
                () ->
                    new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.SESSION_NOT_FOUND));
    Device device = deviceAccessService.requireAuthenticatedDevice();
    if (!session.getDeviceId().equals(device.getId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    if (session.getStatus() == ScreenSessionStatus.ENDED
        || session.getStatus() == ScreenSessionStatus.FAILED) {
      return;
    }

    Instant newerLive =
        findNewerLiveSessionStartedAt(device.getId(), session.getId(), session.getStartedAt());
    StreamFailureGatePolicy.Decision gate =
        StreamFailureGatePolicy.decide(
            Instant.now(), session.getStartedAt(), session.getStatus(), reason, newerLive);
    if (gate != StreamFailureGatePolicy.Decision.ACCEPT) {
      StreamTraceLogger.info(
          "Backend",
          "GATE",
          "reportStreamFailure",
          "ignored failure report",
          Map.of(
              "sessionId",
              sessionId,
              "gate",
              gate.name(),
              "reason",
              reason != null ? reason : "unknown"));
      streamObservabilityService.recordFunnelFailure(session.getStreamType(), reason, gate.name());
      return;
    }

    String reasonCode = StreamFailureReasonClassifier.classify(reason, session.getStatus());
    StreamTraceLogger.transitionState(
        "Backend",
        StreamState.FAILED,
        "reportStreamFailure",
        Map.of(
            "sessionId",
            sessionId,
            "reason",
            reasonCode,
            "rawReason",
            reason != null ? reason : "unknown"));

    session.setStatus(ScreenSessionStatus.FAILED);
    session.setEndedAt(Instant.now());
    screenSessionRepository.save(session);
    streamObservabilityService.recordStreamFailure(session.getStreamType(), reasonCode);
    streamObservabilityService.recordFunnelFailure(session.getStreamType(), reasonCode, null);

    screenCaptureAuditLogDao
        .findFirstBySessionIdAndEndedAtIsNull(sessionId)
        .ifPresent(
            a -> {
              a.setEndedAt(Instant.now());
              screenCaptureAuditLogDao.save(a);
            });

    parentProfileRepository
        .findUserIdByProfileId(device.getParentProfileId())
        .ifPresent(
            parentUserId ->
                realtimeEventService.publishStreamingState(
                    parentUserId,
                    device.getId(),
                    session.getSignalingRoomId(),
                    "FAILED",
                    session.getStreamType(),
                    reasonCode));

    auditService.log(
        AuditActionConstants.STREAM_FAILED,
        "session:" + sessionId,
        device.getId(),
        Map.of(
            "reason",
            reasonCode,
            "rawReason",
            reason != null ? reason : "unknown",
            "roomId",
            session.getSignalingRoomId()));
    signalingCleanupScheduler.purgeRoom(session.getSignalingRoomId());
    fcmWakeService.wakeDevice(device, "STREAM_FAILED", Map.of("sessionId", sessionId.toString()));
  }

  /**
   * StartedAt of a newer PENDING/ACTIVE session on the same device, if any — used to ignore late
   * failure reports for an older session after the parent started a new one.
   */
  private Instant findNewerLiveSessionStartedAt(
      UUID deviceId, UUID sessionId, Instant sessionStartedAt) {
    Instant newest = null;
    for (ScreenSessionStatus status :
        List.of(ScreenSessionStatus.PENDING, ScreenSessionStatus.ACTIVE)) {
      for (ScreenSession other :
          screenSessionRepository.findByDeviceIdAndStatusOrderByStartedAtDesc(deviceId, status)) {
        if (other.getId().equals(sessionId) || other.getStartedAt() == null) {
          continue;
        }
        if (sessionStartedAt != null && !other.getStartedAt().isAfter(sessionStartedAt)) {
          continue;
        }
        if (newest == null || other.getStartedAt().isAfter(newest)) {
          newest = other.getStartedAt();
        }
      }
    }
    return newest;
  }

  /**
   * End any pending/active stream for this device when parent starts a new one so only one viewer
   * (app or web) owns the live session. Notify parents with STREAMING_STATE ENDED so the previous
   * client tears down its WebRTC viewer.
   *
   * <p>Do <strong>not</strong> publish device stream-stop here. A hard stop races the new
   * stream-request (afterCommit): the child tears down camera/screen FGS, then fails to re-acquire
   * from background — web releases and the taking-over app never goes live. The new stream-request
   * / session poll preempts the old publisher on the child instead.
   */
  private void endStalePendingSessions(UUID deviceId, String streamType) {
    Device device = deviceAccessService.requireOwnedDevice(deviceId);
    Optional<UUID> parentUserId =
        parentProfileRepository.findUserIdByProfileId(device.getParentProfileId());

    for (ScreenSessionStatus status :
        List.of(ScreenSessionStatus.PENDING, ScreenSessionStatus.ACTIVE)) {
      screenSessionRepository
          .findByDeviceIdAndStatusOrderByStartedAtDesc(deviceId, status)
          .forEach(
              session -> {
                session.setStatus(ScreenSessionStatus.ENDED);
                session.setEndedAt(Instant.now());
                screenSessionRepository.save(session);
                signalingCleanupScheduler.purgeRoom(session.getSignalingRoomId());
                parentUserId.ifPresent(
                    userId ->
                        realtimeEventService.publishStreamingState(
                            userId,
                            deviceId,
                            session.getSignalingRoomId(),
                            "ENDED",
                            session.getStreamType() != null
                                ? session.getStreamType()
                                : streamType));
              });
    }
  }

  private ScreenShareSessionResponse toSessionResponse(ScreenSession s) {
    return ScreenShareSessionResponse.builder()
        .sessionId(s.getId())
        .deviceId(s.getDeviceId())
        .signalingRoomId(s.getSignalingRoomId())
        .streamType(s.getStreamType())
        .status(s.getStatus())
        .startedAt(s.getStartedAt())
        .build();
  }

  private String normalizeCameraFacing(String cameraFacing, String streamType) {
    if (!AppConstants.STREAM_TYPE_CAMERA.equalsIgnoreCase(streamType)) {
      return null;
    }
    if (cameraFacing == null || cameraFacing.isBlank()) {
      return "user";
    }
    String normalized = cameraFacing.trim().toLowerCase();
    if ("environment".equals(normalized) || "back".equals(normalized)) {
      return "environment";
    }
    return "user";
  }

  private String resolveCaptureMode(Device device, String streamType) {
    if (AppConstants.STREAM_TYPE_SCREEN.equals(streamType)) {
      if (device.isDeviceOwnerActive() && device.isMediaProjectionGranted()) {
        return "NATIVE";
      }
      return "RN_DISPLAY_MEDIA";
    }
    if (AppConstants.STREAM_TYPE_CAMERA.equals(streamType)) {
      return "RN_CAMERA";
    }
    if (AppConstants.STREAM_TYPE_AUDIO.equals(streamType)) {
      return "RN_AUDIO";
    }
    return "RN_DISPLAY_MEDIA";
  }

  private void assertStreamingAllowed(UUID deviceId, String streamType) {
    List<UUID> policyIds = policyAssignmentRepository.findActivePolicyIdsByDeviceId(deviceId);
    if (policyIds.isEmpty()) {
      return;
    }
    boolean hasStreamingPolicy =
        policyRepository.findAllById(policyIds).stream()
            .anyMatch(p -> p.getPolicyType() == PolicyType.STREAMING && p.getEnabled());
    if (!hasStreamingPolicy) {
      return;
    }
    for (UUID policyId : policyIds) {
      streamingPolicyRuleRepository
          .findByPolicyId(policyId)
          .ifPresent(
              rule -> {
                switch (streamType) {
                  case AppConstants.STREAM_TYPE_SCREEN -> {
                    if (!rule.getScreenShareAllowed()) {
                      throw new BusinessException(
                          ErrorCode.FORBIDDEN, MessageConstants.SCREEN_SHARING_DISABLED_BY_POLICY);
                    }
                  }
                  case AppConstants.STREAM_TYPE_CAMERA -> {
                    if (!rule.getCameraAllowed()) {
                      throw new BusinessException(
                          ErrorCode.FORBIDDEN,
                          MessageConstants.CAMERA_STREAMING_DISABLED_BY_POLICY);
                    }
                  }
                  case AppConstants.STREAM_TYPE_AUDIO -> {
                    if (!rule.getAudioAllowed()) {
                      throw new BusinessException(
                          ErrorCode.FORBIDDEN, MessageConstants.AUDIO_STREAMING_DISABLED_BY_POLICY);
                    }
                  }
                  default -> {}
                }
              });
    }
  }

  /**
   * Screen sessions are always startable from the parent. Live MediaProjection consent may be
   * missing ({@code NEVER_GRANTED} / lost after restart) — the child launches the system Cast
   * dialog (Accessibility auto-approves when Clock Time is enabled) and reports failure via
   * streaming state if consent is declined. Pre-blocking here prevented that prompt path.
   */
  private void assertScreenCaptureAvailable(Device device, String streamType) {
    // Intentionally no-op for SCREEN: create the session and notify the child so it can
    // obtain / restore MediaProjection consent during stream bootstrap.
  }

  /**
   * Validates that the child device is reachable before starting a streaming session.
   *
   * <p>Matches parent UI ({@code isDevicePresentOnline}): a fresh {@code lastSeenAt} within the
   * presence window counts as online even when {@code status} still says OFFLINE (status can lag
   * after heartbeats while FCM stream wakes still work).
   */
  private void assertDeviceOnline(Device device, String streamType) {
    if (device.getStatus() == DeviceStatus.REVOKED) {
      throw deviceOfflineException(streamType);
    }
    if (device.getStatus() == DeviceStatus.ONLINE) {
      Instant lastSeen = device.getLastSeenAt();
      if (lastSeen == null
          || !lastSeen.isBefore(
              Instant.now().minus(DevicePresenceScheduler.OFFLINE_THRESHOLD_MINUTES, ChronoUnit.MINUTES))) {
        return;
      }
    } else {
      Instant lastSeen = device.getLastSeenAt();
      if (lastSeen != null
          && !lastSeen.isBefore(
              Instant.now().minus(DevicePresenceScheduler.OFFLINE_THRESHOLD_MINUTES, ChronoUnit.MINUTES))) {
        return;
      }
    }
    throw deviceOfflineException(streamType);
  }

  private BusinessException deviceOfflineException(String streamType) {
    String streamName =
        switch (streamType) {
          case AppConstants.STREAM_TYPE_SCREEN -> "screen sharing";
          case AppConstants.STREAM_TYPE_CAMERA -> "camera view";
          case AppConstants.STREAM_TYPE_AUDIO -> "listening";
          default -> "streaming";
        };
    return new BusinessException(
        ErrorCode.DEVICE_OFFLINE,
        "Child device is offline. Please switch on the child device network connection and try "
            + streamName
            + " again.");
  }
}
