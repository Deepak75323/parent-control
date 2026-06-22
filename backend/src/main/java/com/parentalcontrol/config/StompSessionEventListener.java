package com.parentalcontrol.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * STOMP session lifecycle metrics and structured logs for multi-replica operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompSessionEventListener {

    private final MeterRegistry meterRegistry;

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        meterRegistry.counter("stomp.sessions.connected").increment();
        log.debug("STOMP session connected sessionId={}", accessor.getSessionId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        meterRegistry.counter("stomp.sessions.disconnected").increment();
        log.debug(
                "STOMP session disconnected sessionId={} closeStatus={}",
                accessor.getSessionId(),
                event.getCloseStatus()
        );
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        meterRegistry.counter("stomp.subscriptions", "destination", safeDestination(accessor)).increment();
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        meterRegistry.counter("stomp.unsubscriptions").increment();
    }

    private static String safeDestination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return "unknown";
        }
        // Avoid high-cardinality device/session ids in metric tags.
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
