package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.monitoring.repository.SignalingMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Purges stale WebRTC signaling payloads (SDP/ICE) to limit replay and storage exposure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignalingCleanupScheduler {

    private static final long RETENTION_HOURS = 24;

    private final SignalingMessageRepository signalingMessageRepository;

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void purgeExpiredSignalingMessages() {
        Instant cutoff = Instant.now().minus(RETENTION_HOURS, ChronoUnit.HOURS);
        int deleted = signalingMessageRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} signaling message(s) older than {}h", deleted, RETENTION_HOURS);
        }
    }

    @Transactional
    public void purgeRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }
        signalingMessageRepository.deleteByRoomId(roomId);
        log.debug("Purged signaling messages for room {}", roomId);
    }
}
