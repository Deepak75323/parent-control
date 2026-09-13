package com.parentalcontrol.config;

import com.parentalcontrol.chat.service.ParentPresenceFanoutService;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/** STOMP session lifecycle metrics and parent Online / Last seen presence. */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompSessionEventListener {

  private final MeterRegistry meterRegistry;
  private final ParentPresenceFanoutService parentPresenceFanoutService;

  @EventListener
  public void onConnect(SessionConnectEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    UUID userId = resolveUserId(accessor);
    String sessionId = accessor.getSessionId();
    if (userId != null && sessionId != null) {
      try {
        parentPresenceFanoutService.onSessionConnected(userId, sessionId);
      } catch (Exception e) {
        log.warn("Parent presence connect failed userId={} sessionId={}", userId, sessionId, e);
      }
    }
  }

  @EventListener
  public void onConnected(SessionConnectedEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    meterRegistry.counter("stomp.sessions.connected").increment();
    log.debug("STOMP session connected sessionId={}", accessor.getSessionId());
  }

  @EventListener
  public void onDisconnect(SessionDisconnectEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    meterRegistry.counter("stomp.sessions.disconnected").increment();
    UUID userId = resolveUserId(accessor);
    String sessionId = accessor.getSessionId();
    if (userId != null && sessionId != null) {
      try {
        parentPresenceFanoutService.onSessionDisconnected(userId, sessionId);
      } catch (Exception e) {
        log.warn("Parent presence disconnect failed userId={} sessionId={}", userId, sessionId, e);
      }
    }
    log.debug(
        "STOMP session disconnected sessionId={} closeStatus={}",
        sessionId,
        event.getCloseStatus());
  }

  @EventListener
  public void onSubscribe(SessionSubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    meterRegistry
        .counter("stomp.subscriptions", "destination", safeDestination(accessor))
        .increment();
  }

  @EventListener
  public void onUnsubscribe(SessionUnsubscribeEvent event) {
    meterRegistry.counter("stomp.unsubscriptions").increment();
  }

  static UUID resolveUserId(StompHeaderAccessor accessor) {
    Principal principal = accessor.getUser();
    if (principal instanceof UsernamePasswordAuthenticationToken token) {
      Object inner = token.getPrincipal();
      if (inner instanceof StompPrincipal stomp) {
        return stomp.userId();
      }
    }
    if (principal instanceof StompPrincipal stomp) {
      return stomp.userId();
    }
    if (principal != null && principal.getName() != null) {
      try {
        return UUID.fromString(principal.getName());
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
    return null;
  }

  private static String safeDestination(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null) {
      return "unknown";
    }
    if (destination.startsWith("/topic/device/")) {
      return "/topic/device/{id}/…";
    }
    if (destination.startsWith("/topic/signaling/")) {
      return "/topic/signaling/{room}";
    }
    if (destination.startsWith("/user/queue/")) {
      return "/user/queue/{name}";
    }
    return destination;
  }
}
