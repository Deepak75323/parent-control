package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.monitoring.domain.ScreenSession;
import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import com.parentalcontrol.monitoring.dto.ScreenShareSessionResponse;
import com.parentalcontrol.monitoring.dto.StartScreenShareRequest;
import com.parentalcontrol.monitoring.dto.StartStreamRequest;
import com.parentalcontrol.monitoring.domain.ScreenCaptureAuditLog;
import com.parentalcontrol.monitoring.repository.ScreenCaptureAuditLogRepository;
import com.parentalcontrol.monitoring.repository.ScreenSessionRepository;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.policy.domain.PolicyType;
import com.parentalcontrol.user.service.ParentPinService;
import com.parentalcontrol.policy.domain.StreamingPolicyRule;
import com.parentalcontrol.policy.repository.PolicyAssignmentRepository;
import com.parentalcontrol.policy.repository.PolicyRepository;
import com.parentalcontrol.policy.repository.StreamingPolicyRuleRepository;
import com.parentalcontrol.user.repository.ParentProfileRepository;
import com.parentalcontrol.observability.StreamObservabilityService;
import com.parentalcontrol.observability.CorrelationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StreamingService {

    private final ScreenSessionRepository screenSessionRepository;
    private final DeviceAccessService deviceAccessService;
    private final RealtimeEventService realtimeEventService;
    private final ParentProfileRepository parentProfileRepository;
    private final PolicyAssignmentRepository policyAssignmentRepository;
    private final PolicyRepository policyRepository;
    private final StreamingPolicyRuleRepository streamingPolicyRuleRepository;
    private final ParentPinService parentPinService;
    private final ScreenCaptureAuditLogRepository screenCaptureAuditLogRepository;
    private final AuditService auditService;
    private final FeatureGateService featureGateService;
    private final SignalingCleanupScheduler signalingCleanupScheduler;
    private final com.parentalcontrol.device.service.FcmWakeService fcmWakeService;
    private final StreamObservabilityService streamObservabilityService;

    @Transactional
    public ScreenShareSessionResponse startScreenShare(StartScreenShareRequest request) {
        return startStream(request.getDeviceId(), "SCREEN");
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
    public ScreenShareSessionResponse startStream(UUID deviceId, String streamType, String parentPin) {
        return startStream(deviceId, streamType, parentPin, null);
    }

    @Transactional
    public ScreenShareSessionResponse startStream(
            UUID deviceId,
            String streamType,
            String parentPin,
            String cameraFacing) {
        Device device = deviceAccessService.requireOwnedDevice(deviceId);
        featureGateService.requireLiveStreaming(device.getParentProfileId());
        assertStreamingAllowed(device.getId(), streamType);
        assertScreenCaptureAvailable(device, streamType);
        endStalePendingSessions(device.getId(), streamType);

        String normalizedFacing = normalizeCameraFacing(cameraFacing, streamType);
        String captureMode = resolveCaptureMode(device, streamType);
        if ("NATIVE".equals(captureMode)) {
            parentPinService.requireSilentStreamAccess(parentPin);
        }

        ScreenSession session = screenSessionRepository.save(ScreenSession.builder()
                .deviceId(device.getId())
                .startedAt(Instant.now())
                .status(ScreenSessionStatus.PENDING)
                .streamType(streamType)
                .build());

        CorrelationContext.setSessionId(session.getId());
        streamObservabilityService.recordStreamStart(streamType);

        String roomId = streamType.toLowerCase() + "_" + device.getId() + "_" + session.getId();
        session.setSignalingRoomId(roomId);
        final ScreenSession activeSession = screenSessionRepository.save(session);
        final UUID sessionId = activeSession.getId();

        parentProfileRepository.findUserIdByProfileId(device.getParentProfileId())
                .ifPresent(parentUserId -> realtimeEventService.publishStreamingState(
                        parentUserId, device.getId(), roomId, "PENDING", streamType));
        realtimeEventService.publishDeviceStreamRequest(
                device.getId(), roomId, streamType, sessionId, normalizedFacing);
        fcmWakeService.wakeDevice(device, "STREAM_REQUEST", Map.of(
                "roomId", roomId,
                "streamType", streamType,
                "sessionId", sessionId.toString()));

        UUID parentUserId = SecurityUtils.currentUser().map(u -> u.getId()).orElse(null);
        screenCaptureAuditLogRepository.save(ScreenCaptureAuditLog.builder()
                .deviceId(device.getId())
                .sessionId(sessionId)
                .streamType(streamType)
                .captureMode(captureMode)
                .parentUserId(parentUserId)
                .startedAt(Instant.now())
                .build());

        auditService.log(
                "STREAM_START",
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
                .message("WebRTC signaling room ready. Connect via /api/v1/monitoring/signaling/" + roomId)
                .build();
    }

    @Transactional
    public ScreenShareSessionResponse stopScreenShare(UUID sessionId) {
        ScreenSession session = screenSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Session not found"));

        deviceAccessService.requireOwnedDevice(session.getDeviceId());

        session.setStatus(ScreenSessionStatus.ENDED);
        session.setEndedAt(Instant.now());
        final ScreenSession endedSession = screenSessionRepository.save(session);

        screenCaptureAuditLogRepository.findFirstBySessionIdAndEndedAtIsNull(sessionId)
                .ifPresent(a -> {
                    a.setEndedAt(Instant.now());
                    screenCaptureAuditLogRepository.save(a);
                });

        auditService.log(
                "STREAM_STOP",
                "session:" + sessionId,
                endedSession.getDeviceId(),
                Map.of("roomId", endedSession.getSignalingRoomId()));

        parentProfileRepository.findUserIdByProfileId(
                deviceAccessService.requireOwnedDevice(endedSession.getDeviceId()).getParentProfileId())
                .ifPresent(parentUserId -> realtimeEventService.publishStreamingState(
                        parentUserId,
                        endedSession.getDeviceId(),
                        endedSession.getSignalingRoomId(),
                        "ENDED",
                        endedSession.getStreamType()));

        realtimeEventService.publishDeviceStreamStop(
                endedSession.getDeviceId(),
                endedSession.getId(),
                endedSession.getSignalingRoomId());

        signalingCleanupScheduler.purgeRoom(endedSession.getSignalingRoomId());

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
        return screenSessionRepository.findByDeviceIdAndStatusOrderByStartedAtDesc(
                device.getId(), ScreenSessionStatus.PENDING).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ScreenShareSessionResponse> activeSessionForDevice() {
        Device device = deviceAccessService.requireAuthenticatedDevice();
        return screenSessionRepository
                .findFirstByDeviceIdAndStatusOrderByStartedAtDesc(device.getId(), ScreenSessionStatus.ACTIVE)
                .map(this::toSessionResponse);
    }

    @Transactional
    public void switchCamera(UUID sessionId) {
        ScreenSession session = screenSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Session not found"));
        deviceAccessService.requireOwnedDevice(session.getDeviceId());
        if (!"CAMERA".equalsIgnoreCase(session.getStreamType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Camera switch only applies to CAMERA streams");
        }
        if (session.getStatus() != ScreenSessionStatus.ACTIVE && session.getStatus() != ScreenSessionStatus.PENDING) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "No active camera stream");
        }
        realtimeEventService.publishDeviceStreamControl(
                session.getDeviceId(), session.getId(), "SWITCH_CAMERA");
    }

    @Transactional
    public void reportStreamFailure(UUID sessionId, String reason) {
        ScreenSession session = screenSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Session not found"));
        Device device = deviceAccessService.requireAuthenticatedDevice();
        if (!session.getDeviceId().equals(device.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (session.getStatus() == ScreenSessionStatus.ENDED
                || session.getStatus() == ScreenSessionStatus.FAILED) {
            return;
        }
        session.setStatus(ScreenSessionStatus.FAILED);
        session.setEndedAt(Instant.now());
        screenSessionRepository.save(session);
        streamObservabilityService.recordStreamFailure(session.getStreamType(), reason);

        screenCaptureAuditLogRepository.findFirstBySessionIdAndEndedAtIsNull(sessionId)
                .ifPresent(a -> {
                    a.setEndedAt(Instant.now());
                    screenCaptureAuditLogRepository.save(a);
                });

        parentProfileRepository.findUserIdByProfileId(device.getParentProfileId())
                .ifPresent(parentUserId -> realtimeEventService.publishStreamingState(
                        parentUserId,
                        device.getId(),
                        session.getSignalingRoomId(),
                        "FAILED",
                        session.getStreamType(),
                        reason));

        auditService.log(
                "STREAM_FAILED",
                "session:" + sessionId,
                device.getId(),
                Map.of("reason", reason != null ? reason : "unknown", "roomId", session.getSignalingRoomId()));
        signalingCleanupScheduler.purgeRoom(session.getSignalingRoomId());
        fcmWakeService.wakeDevice(device, "STREAM_FAILED", Map.of("sessionId", sessionId.toString()));
    }

    /** End stale sessions of the same stream type when parent starts a new stream. */
    private void endStalePendingSessions(UUID deviceId, String streamType) {
        for (ScreenSessionStatus status : List.of(ScreenSessionStatus.PENDING, ScreenSessionStatus.ACTIVE)) {
            screenSessionRepository.findByDeviceIdAndStatusOrderByStartedAtDesc(deviceId, status)
                    .stream()
                    .filter(session -> streamType.equalsIgnoreCase(session.getStreamType()))
                    .forEach(session -> {
                        session.setStatus(ScreenSessionStatus.ENDED);
                        session.setEndedAt(Instant.now());
                        screenSessionRepository.save(session);
                        signalingCleanupScheduler.purgeRoom(session.getSignalingRoomId());
                        realtimeEventService.publishDeviceStreamStop(
                                deviceId, session.getId(), session.getSignalingRoomId());
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
        if (!"CAMERA".equalsIgnoreCase(streamType)) {
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
        if ("SCREEN".equals(streamType)) {
            if (device.isDeviceOwnerActive() && device.isMediaProjectionGranted()) {
                return "NATIVE";
            }
            return "RN_DISPLAY_MEDIA";
        }
        if ("CAMERA".equals(streamType)) {
            return "RN_CAMERA";
        }
        if ("AUDIO".equals(streamType)) {
            return "RN_AUDIO";
        }
        return "RN_DISPLAY_MEDIA";
    }

    private void assertStreamingAllowed(UUID deviceId, String streamType) {
        List<UUID> policyIds = policyAssignmentRepository.findActivePolicyIdsByDeviceId(deviceId);
        if (policyIds.isEmpty()) {
            return;
        }
        boolean hasStreamingPolicy = policyRepository.findAllById(policyIds).stream()
                .anyMatch(p -> p.getPolicyType() == PolicyType.STREAMING && p.getEnabled());
        if (!hasStreamingPolicy) {
            return;
        }
        for (UUID policyId : policyIds) {
            streamingPolicyRuleRepository.findByPolicyId(policyId).ifPresent(rule -> {
                switch (streamType) {
                    case "SCREEN" -> {
                        if (!rule.getScreenShareAllowed()) {
                            throw new BusinessException(ErrorCode.FORBIDDEN, "Screen sharing disabled by policy");
                        }
                    }
                    case "CAMERA" -> {
                        if (!rule.getCameraAllowed()) {
                            throw new BusinessException(ErrorCode.FORBIDDEN, "Camera streaming disabled by policy");
                        }
                    }
                    case "AUDIO" -> {
                        if (!rule.getAudioAllowed()) {
                            throw new BusinessException(ErrorCode.FORBIDDEN, "Audio streaming disabled by policy");
                        }
                    }
                    default -> { }
                }
            });
        }
    }

    /**
     * Screen capture requires live MediaProjection consent on the child device. The system token
     * holds a binder that cannot survive process death (see {@code MediaProjectionTokenStore}),
     * so we reject doomed sessions early — for every device, not just device-owner — to avoid
     * black-screen failures on the parent side. The child reports its current consent state via
     * heartbeat ({@code mediaProjectionConsentState}).
     */
    private void assertScreenCaptureAvailable(Device device, String streamType) {
        if (!"SCREEN".equals(streamType)) {
            return;
        }
        if (device.isMediaProjectionGranted()) {
            return;
        }
        String state = device.getMediaProjectionConsentState();
        String message = switch (state != null ? state : "NEVER_GRANTED") {
            case "LOST_AFTER_PROCESS_DEATH" ->
                    "Screen capture permission was lost after the child phone restarted. " +
                            "The child must re-approve screen sharing — open the child app → Permissions → Grant screen capture.";
            case "REQUIRES_RECONSENT", "RECOVERY_FAILED" ->
                    "The child must re-approve screen sharing. " +
                            "Open the child app → Permissions → Grant screen capture, then try again.";
            case "RECOVERY_PENDING" ->
                    "The child app is restoring screen capture permission. Wait a moment, then try again.";
            default ->
                    "Screen capture is unavailable on the child device. " +
                            "Open the child app → Permissions → Grant screen capture, then try again.";
        };
        throw new BusinessException(ErrorCode.FORBIDDEN, message);
    }
}
