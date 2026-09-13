package com.parentalcontrol.chat.service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Tracks parent STOMP sessions for Online / Last seen. Redis when available (multi-replica);
 * otherwise in-process maps for single-node / tests.
 */
@Service
@Slf4j
public class ParentPresenceTracker {

  private static final String SESSIONS_KEY_PREFIX = "presence:parent:sessions:";
  private static final String LAST_SEEN_KEY_PREFIX = "presence:parent:lastSeen:";

  private final StringRedisTemplate redisTemplate;
  private final ConcurrentHashMap<UUID, Set<String>> localSessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Instant> localLastSeen = new ConcurrentHashMap<>();

  public ParentPresenceTracker(
      @Autowired(required = false) StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public record Snapshot(boolean online, Instant lastSeenAt) {}

  public record Transition(boolean onlineChanged, boolean nowOnline, Instant lastSeenAt) {}

  public Transition onSessionConnected(UUID userId, String sessionId) {
    if (userId == null || sessionId == null || sessionId.isBlank()) {
      return new Transition(false, false, null);
    }
    Instant now = Instant.now();
    if (redisTemplate != null) {
      String sessionsKey = SESSIONS_KEY_PREFIX + userId;
      Long added = redisTemplate.opsForSet().add(sessionsKey, sessionId);
      redisTemplate.opsForValue().set(LAST_SEEN_KEY_PREFIX + userId, now.toString());
      Long count = redisTemplate.opsForSet().size(sessionsKey);
      boolean becameOnline = added != null && added > 0 && count != null && count == 1L;
      return new Transition(becameOnline, count != null && count > 0, now);
    }
    Set<String> sessions =
        localSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
    boolean added = sessions.add(sessionId);
    localLastSeen.put(userId, now);
    boolean becameOnline = added && sessions.size() == 1;
    return new Transition(becameOnline, !sessions.isEmpty(), now);
  }

  public Transition onSessionDisconnected(UUID userId, String sessionId) {
    if (userId == null || sessionId == null || sessionId.isBlank()) {
      return new Transition(false, false, null);
    }
    Instant now = Instant.now();
    if (redisTemplate != null) {
      String sessionsKey = SESSIONS_KEY_PREFIX + userId;
      Long removed = redisTemplate.opsForSet().remove(sessionsKey, sessionId);
      Long count = redisTemplate.opsForSet().size(sessionsKey);
      boolean becameOffline =
          removed != null && removed > 0 && (count == null || count == 0L);
      if (becameOffline) {
        redisTemplate.opsForValue().set(LAST_SEEN_KEY_PREFIX + userId, now.toString());
        return new Transition(true, false, now);
      }
      Instant lastSeen = readRedisLastSeen(userId);
      return new Transition(false, count != null && count > 0, lastSeen);
    }
    Set<String> sessions = localSessions.get(userId);
    if (sessions == null) {
      return new Transition(false, false, localLastSeen.get(userId));
    }
    boolean removed = sessions.remove(sessionId);
    if (sessions.isEmpty()) {
      localSessions.remove(userId, sessions);
      if (removed) {
        localLastSeen.put(userId, now);
        return new Transition(true, false, now);
      }
      return new Transition(false, false, localLastSeen.get(userId));
    }
    return new Transition(false, true, localLastSeen.get(userId));
  }

  public Snapshot snapshot(UUID userId) {
    if (userId == null) {
      return new Snapshot(false, null);
    }
    if (redisTemplate != null) {
      Long count = redisTemplate.opsForSet().size(SESSIONS_KEY_PREFIX + userId);
      boolean online = count != null && count > 0;
      return new Snapshot(online, readRedisLastSeen(userId));
    }
    Set<String> sessions = localSessions.get(userId);
    boolean online = sessions != null && !sessions.isEmpty();
    return new Snapshot(online, localLastSeen.get(userId));
  }

  private Instant readRedisLastSeen(UUID userId) {
    String raw = redisTemplate.opsForValue().get(LAST_SEEN_KEY_PREFIX + userId);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (Exception e) {
      log.debug("Invalid presence lastSeen for userId={}: {}", userId, e.toString());
      return null;
    }
  }

  /** Test helper — clear local maps. */
  void clearLocalForTests() {
    localSessions.clear();
    localLastSeen.clear();
  }

  Map<UUID, Set<String>> localSessionsForTests() {
    return localSessions;
  }
}
