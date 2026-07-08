package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.monitoring.domain.SignalingMessage;
import com.parentalcontrol.monitoring.domain.ScreenSession;
import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import com.parentalcontrol.monitoring.dto.SignalingMessageRequest;
import com.parentalcontrol.monitoring.dto.SignalingMessageResponse;
import com.parentalcontrol.monitoring.repository.ScreenSessionRepository;
import com.parentalcontrol.monitoring.repository.SignalingMessageRepository;
import com.parentalcontrol.user.repository.ParentProfileRepository;
import com.parentalcontrol.observability.StreamObservabilityService;
import com.parentalcontrol.observability.StreamTraceLogger;
import com.parentalcontrol.observability.StreamState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SignalingService {

    private final SignalingMessageRepository signalingMessageRepository;
    private final ScreenSessionRepository screenSessionRepository;
    private final DeviceAccessService deviceAccessService;
    private final RealtimeEventService realtimeEventService;
    private final ParentProfileRepository parentProfileRepository;
    private final StreamObservabilityService streamObservabilityService;

    @Transactional
    public SignalingMessageResponse postMessage(String roomId, SignalingMessageRequest request) {
        String senderRole = resolveSenderRole();
        StreamTraceLogger.info("Backend", "SIGNALING", "postMessage", "incoming signaling API",
                Map.of("roomId", roomId, "messageType", request.getMessageType(), "senderRole", senderRole,
                        "sessionId", request.getSessionId()));
        validateRoomAccess(roomId, senderRole);
        requireSessionId(request.getSessionId());
        validateSessionMatchesRoom(request.getSessionId(), roomId);
        validateSessionForRoom(request.getSessionId(), roomId, senderRole, request.getMessageType(), request.getPayload());

        SignalingMessage entity = SignalingMessage.builder()
                .roomId(roomId)
                .sessionId(request.getSessionId())
                .senderRole(senderRole)
                .messageType(request.getMessageType())
                .payload(request.getPayload())
                .build();
        entity = signalingMessageRepository.save(entity);

        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("roomId", roomId);
        wsPayload.put("messageType", request.getMessageType());
        wsPayload.put("payload", request.getPayload());
        wsPayload.put("senderRole", senderRole);
        wsPayload.put("sessionId", request.getSessionId() != null ? request.getSessionId().toString() : null);
        wsPayload.put("id", entity.getId().toString());
        String trace = StreamTraceLogger.traceId();
        if (trace != null && !trace.isBlank()) {
            wsPayload.put("traceId", trace);
        }
        realtimeEventService.publishSignalingMessage(roomId, wsPayload);
        StreamTraceLogger.info("Backend", "SOCKET", "postMessage", "STOMP signaling emitted",
                Map.of("roomId", roomId, "messageType", request.getMessageType(), "id", entity.getId()));

        if ("CHILD".equals(senderRole) && "offer".equals(request.getMessageType())) {
            StreamTraceLogger.transitionState("Backend", StreamState.LOCAL_DESCRIPTION_SET, "postMessage",
                    Map.of("roomId", roomId, "sessionId", request.getSessionId()));
            markSessionActive(request.getSessionId(), roomId);
        }
        if ("PARENT".equals(senderRole) && "answer".equals(request.getMessageType())) {
            StreamTraceLogger.transitionState("Backend", StreamState.REMOTE_DESCRIPTION_SET, "postMessage",
                    Map.of("roomId", roomId, "sessionId", request.getSessionId()));
        }
        if ("ice".equals(request.getMessageType())) {
            StreamTraceLogger.transitionState("Backend", StreamState.ICE_CONNECTING, "postMessage",
                    Map.of("roomId", roomId, "messageType", "ice"));
        }

        return SignalingMessageResponse.builder()
                .id(entity.getId())
                .roomId(roomId)
                .messageType(request.getMessageType())
                .payload(request.getPayload())
                .senderRole(senderRole)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SignalingMessageResponse> getRecentMessages(String roomId) {
        validateRoomAccess(roomId, resolveSenderRole());
        rejectSignalingForInactiveSession(roomId);
        List<SignalingMessageResponse> newestFirst = signalingMessageRepository
                .findTop50ByRoomIdOrderByCreatedAtDesc(roomId).stream()
                .map(m -> SignalingMessageResponse.builder()
                        .id(m.getId())
                        .roomId(m.getRoomId())
                        .messageType(m.getMessageType())
                        .payload(m.getPayload())
                        .senderRole(m.getSenderRole())
                        .build())
                .toList();
        // WebRTC clients expect chronological order (offer/answer before ICE).
        return newestFirst.reversed();
    }

    private String resolveSenderRole() {
        if (SecurityUtils.getDeviceId().isPresent()) {
            return "CHILD";
        }
        return "PARENT";
    }

    private void validateRoomAccess(String roomId, String senderRole) {
        UUID deviceId = extractDeviceIdFromRoom(roomId);
        if ("CHILD".equals(senderRole)) {
            Device device = deviceAccessService.requireAuthenticatedDevice();
            if (!device.getId().equals(deviceId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        } else {
            deviceAccessService.requireOwnedDevice(deviceId);
        }
    }

    private void validateSessionForRoom(
            UUID sessionId,
            String roomId,
            String senderRole,
            String messageType,
            String payload) {
        UUID roomDeviceId = extractDeviceIdFromRoom(roomId);
        ScreenSession session = screenSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Streaming session not found"));
        if (!session.getDeviceId().equals(roomDeviceId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Session does not belong to signaling room");
        }
        if (session.getStatus() == ScreenSessionStatus.ENDED
                || session.getStatus() == ScreenSessionStatus.FAILED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Streaming session is not active");
        }
        if ("CHILD".equals(senderRole)) {
            Device device = deviceAccessService.requireAuthenticatedDevice();
            if (!session.getDeviceId().equals(device.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        rejectDuplicateSignalingPayload(roomId, messageType, payload);
    }

    private void requireSessionId(UUID sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sessionId is required for signaling");
        }
    }

    private void validateSessionMatchesRoom(UUID sessionId, String roomId) {
        UUID embeddedSessionId = extractSessionIdFromRoom(roomId);
        if (!embeddedSessionId.equals(sessionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "sessionId does not match signaling room");
        }
    }

    private UUID extractSessionIdFromRoom(String roomId) {
        String[] parts = roomId.split("_");
        if (parts.length < 3) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid signaling room id — session required");
        }
        try {
            return UUID.fromString(parts[2]);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid signaling room id");
        }
    }

    /** Reject identical SDP payloads replayed within a short window (offer/answer only). */
    private void rejectDuplicateSignalingPayload(String roomId, String messageType, String payload) {
        if (roomId == null || payload == null || payload.isBlank()) {
            return;
        }
        if (!"offer".equals(messageType) && !"answer".equals(messageType) && !"ice".equals(messageType)) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(120);
        boolean duplicate = signalingMessageRepository
                .findTop50ByRoomIdOrderByCreatedAtDesc(roomId)
                .stream()
                .anyMatch(m -> messageType.equals(m.getMessageType())
                        && payload.equals(m.getPayload())
                        && m.getCreatedAt() != null
                        && m.getCreatedAt().isAfter(cutoff));
        if (duplicate) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Duplicate signaling message rejected");
        }
    }

    private UUID extractDeviceIdFromRoom(String roomId) {
        try {
            String[] parts = roomId.split("_");
            if (parts.length >= 2) {
                return UUID.fromString(parts[1]);
            }
        } catch (Exception ignored) {
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid signaling room id");
    }

    /** Block SDP/ICE replay after session ended or failed. */
    private void rejectSignalingForInactiveSession(String roomId) {
        UUID sessionId = extractSessionIdFromRoom(roomId);
        screenSessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getStatus() == ScreenSessionStatus.ENDED
                    || session.getStatus() == ScreenSessionStatus.FAILED) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Signaling unavailable — streaming session has ended");
            }
        });
    }

    private void markSessionActive(UUID sessionId, String roomId) {
        if (sessionId == null) {
            return;
        }
        screenSessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getStatus() != ScreenSessionStatus.PENDING) {
                return;
            }
            session.setStatus(ScreenSessionStatus.ACTIVE);
            screenSessionRepository.save(session);
            streamObservabilityService.recordStreamActive(session);
            StreamTraceLogger.transitionState("Backend", StreamState.STREAMING, "markSessionActive",
                    Map.of("sessionId", sessionId, "roomId", roomId));
            Device device = deviceAccessService.requireAuthenticatedDevice();
            parentProfileRepository.findUserIdByProfileId(device.getParentProfileId())
                    .ifPresent(parentUserId -> realtimeEventService.publishStreamingState(
                            parentUserId,
                            device.getId(),
                            roomId,
                            "ACTIVE",
                            session.getStreamType()));
        });
    }
}
