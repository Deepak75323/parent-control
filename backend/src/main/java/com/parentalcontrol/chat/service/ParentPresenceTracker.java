package com.parentalcontrol.chat.service;

import java.time.Duration;
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
 *
 * <p>Session → user reverse index lets disconnect clean up when the STOMP principal is already
 * gone (common on abrupt close) so rooms do not stay stuck Online forever.
 */
@Service
@Slf4j
public class ParentPresenceTracker {

  private static final String SESSIONS_KEY_PREFIX = "presence:parent:sessions:";
  private static final String LAST_SEEN_KEY_PREFIX = "presence:parent:lastSeen:";
  private static final String SESSION_OWNER_KEY_PREFIX = "presence:parent:owner:";
  /** Bound ghost sessions if disconnect never arrives (deploy / network drop). */
  private static final Duration SESSION_OWNER_TTL = Duration.ofHours(12);

  private final StringRedisTemplate redisTemplate;
  private final ConcurrentHashMap<UUID, Set<String>> localSessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Instant> localLastSeen = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, UUID> localSessionOwners = new ConcurrentHashMap<>();

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
    rememberSessionOwner(sessionId, userId);
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
    forgetSessionOwner(sessionId);
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

  /**
   * Disconnect path when the STOMP principal is missing — look up the user from the session id
   * recorded on connect.
   */
  public Transition onSessionDisconnectedBySessionId(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return new Transition(false, false, null);
    }
    UUID userId = lookupSessionOwner(sessionId);
    if (userId == null) {
      return new Transition(false, false, null);
    }
    return onSessionDisconnected(userId, sessionId);
  }

  /** Who owns this STOMP session id (connect-time reverse index). */
  public UUID peekSessionOwner(String sessionId) {
    return lookupSessionOwner(sessionId);
  }

  /** Clear every tracked session for a user (ghost Redis rows after missed disconnect). */
  public Transition forceOffline(UUID userId) {
    if (userId == null) {
      return new Transition(false, false, null);
    }
    Snapshot before = snapshot(userId);
    if (!before.online()) {
      return new Transition(false, false, before.lastSeenAt());
    }
    Instant now = Instant.now();
    if (redisTemplate != null) {
      String sessionsKey = SESSIONS_KEY_PREFIX + userId;
      Set<String> members = redisTemplate.opsForSet().members(sessionsKey);
      if (members != null) {
        for (String sessionId : members) {
          forgetSessionOwner(sessionId);
        }
      }
      redisTemplate.delete(sessionsKey);
      redisTemplate.opsForValue().set(LAST_SEEN_KEY_PREFIX + userId, now.toString());
      return new Transition(true, false, now);
    }
    Set<String> sessions = localSessions.remove(userId);
    if (sessions != null) {
      for (String sessionId : sessions) {
        localSessionOwners.remove(sessionId);
      }
    }
    localLastSeen.put(userId, now);
    return new Transition(true, false, now);
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

  private void rememberSessionOwner(String sessionId, UUID userId) {
    localSessionOwners.put(sessionId, userId);
    if (redisTemplate != null) {
      redisTemplate
          .opsForValue()
          .set(SESSION_OWNER_KEY_PREFIX + sessionId, userId.toString(), SESSION_OWNER_TTL);
    }
  }

  private void forgetSessionOwner(String sessionId) {
    localSessionOwners.remove(sessionId);
    if (redisTemplate != null) {
      redisTemplate.delete(SESSION_OWNER_KEY_PREFIX + sessionId);
    }
  }

  private UUID lookupSessionOwner(String sessionId) {
    UUID local = localSessionOwners.get(sessionId);
    if (local != null) {
      return local;
    }
    if (redisTemplate == null) {
      return null;
    }
    String raw = redisTemplate.opsForValue().get(SESSION_OWNER_KEY_PREFIX + sessionId);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      return null;
    }
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
    localSessionOwners.clear();
  }

  Map<UUID, Set<String>> localSessionsForTests() {
    return localSessions;
  }
}
