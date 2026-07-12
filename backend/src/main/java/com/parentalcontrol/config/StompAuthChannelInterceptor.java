package com.parentalcontrol.config;

import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.domain.TokenType;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.device.security.DeviceTokenValidationService;
import com.parentalcontrol.monitoring.service.DeviceAccessService;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  private final JwtTokenProvider jwtTokenProvider;
  private final DeviceAccessService deviceAccessService;
  private final DeviceTokenValidationService deviceTokenValidationService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      return authenticateConnect(accessor, message);
    }

    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
        || StompCommand.SEND.equals(accessor.getCommand())) {
      StompPrincipal principal = resolvePrincipal(accessor);
      if (principal == null) {
        throw new MessageDeliveryException("Unauthorized STOMP " + accessor.getCommand());
      }
      String destination = accessor.getDestination();
      if (destination != null && destination.startsWith("/topic/device/")) {
        validateDeviceTopic(principal, destination);
      } else if (destination != null && destination.startsWith("/topic/signaling/")) {
        validateSignalingTopic(principal, destination);
      } else if (destination != null && destination.startsWith("/topic/parent/")) {
        validateParentTopic(principal, destination);
      } else if (destination != null && destination.startsWith("/user/queue/")) {
        validateUserQueue(principal, destination);
      } else {
        throw new MessageDeliveryException(
            "Forbidden STOMP destination: " + (destination == null ? "<null>" : destination));
      }
    }

    return message;
  }

  private Message<?> authenticateConnect(StompHeaderAccessor accessor, Message<?> message) {
    String authHeader = accessor.getFirstNativeHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new MessageDeliveryException("Unauthorized STOMP CONNECT — missing Bearer token");
    }

    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      throw new MessageDeliveryException("Unauthorized STOMP CONNECT — invalid token");
    }

    Claims claims = jwtTokenProvider.parseClaims(token);
    TokenType tokenType = jwtTokenProvider.getTokenType(claims);
    if (tokenType == TokenType.REFRESH) {
      throw new MessageDeliveryException("Unauthorized STOMP CONNECT — refresh tokens not allowed");
    }
    if (tokenType == TokenType.DEVICE) {
      deviceTokenValidationService.validateDeviceToken(claims);
    }

    Role role = jwtTokenProvider.getRole(claims);
    StompPrincipal principal =
        new StompPrincipal(
            jwtTokenProvider.getUserId(claims),
            claims.get("email", String.class),
            role,
            jwtTokenProvider.getDeviceId(claims));

    accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    return message;
  }

  private StompPrincipal resolvePrincipal(StompHeaderAccessor accessor) {
    if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)) {
      return null;
    }
    if (!(auth.getPrincipal() instanceof StompPrincipal principal)) {
      return null;
    }
    return principal;
  }

  private void validateDeviceTopic(StompPrincipal principal, String destination) {
    UUID topicDeviceId = extractDeviceIdFromTopic(destination);
    if (principal.role() == Role.ROLE_CHILD_DEVICE) {
      if (principal.deviceId() == null || !principal.deviceId().equals(topicDeviceId)) {
        throw new MessageDeliveryException("Child device cannot subscribe to foreign topic");
      }
      return;
    }
    if (principal.role() == Role.ROLE_PARENT) {
      deviceAccessService.requireOwnedDevice(topicDeviceId);
    }
  }

  private UUID extractDeviceIdFromTopic(String destination) {
    // /topic/device/{uuid}/commands
    String[] parts = destination.split("/");
    if (parts.length < 4) {
      throw new MessageDeliveryException("Invalid device topic: " + destination);
    }
    try {
      return UUID.fromString(parts[3]);
    } catch (IllegalArgumentException e) {
      throw new MessageDeliveryException("Invalid device id in topic: " + destination);
    }
  }

  /** /topic/signaling/{streamType}_{deviceId}_{sessionId} */
  private void validateSignalingTopic(StompPrincipal principal, String destination) {
    UUID roomDeviceId = extractDeviceIdFromSignalingRoom(destination);
    if (principal.role() == Role.ROLE_CHILD_DEVICE) {
      if (principal.deviceId() == null || !principal.deviceId().equals(roomDeviceId)) {
        throw new MessageDeliveryException(
            "Child device cannot subscribe to foreign signaling room");
      }
      return;
    }
    if (principal.role() == Role.ROLE_PARENT) {
      deviceAccessService.requireOwnedDevice(roomDeviceId);
    }
  }

  private UUID extractDeviceIdFromSignalingRoom(String destination) {
    // /topic/signaling/screen_{uuid}_{sessionId}
    String[] parts = destination.split("/");
    if (parts.length < 4) {
      throw new MessageDeliveryException("Invalid signaling topic: " + destination);
    }
    String roomId = parts[3];
    String[] roomParts = roomId.split("_");
    if (roomParts.length < 2) {
      throw new MessageDeliveryException("Invalid signaling room id: " + roomId);
    }
    try {
      return UUID.fromString(roomParts[1]);
    } catch (IllegalArgumentException e) {
      throw new MessageDeliveryException("Invalid device id in signaling room: " + roomId);
    }
  }

  /** Parent-private queues — child devices must not subscribe. */
  private void validateUserQueue(StompPrincipal principal, String destination) {
    if (principal.role() == Role.ROLE_CHILD_DEVICE) {
      throw new MessageDeliveryException("Child device cannot subscribe to parent user queues");
    }
    if (principal.role() != Role.ROLE_PARENT) {
      throw new MessageDeliveryException("Unauthorized user queue subscription");
    }
    // Spring routes /user/queue/* to the authenticated principal only — block SEND on user queues.
    if (destination.contains("/queue/") && !destination.startsWith("/user/queue/")) {
      throw new MessageDeliveryException("Invalid user queue destination: " + destination);
    }
  }

  /** /topic/parent/{parentUserId}/alerts */
  private void validateParentTopic(StompPrincipal principal, String destination) {
    if (principal.role() != Role.ROLE_PARENT) {
      throw new MessageDeliveryException("Only parents may subscribe to parent topics");
    }
    String[] parts = destination.split("/");
    if (parts.length < 4) {
      throw new MessageDeliveryException("Invalid parent topic: " + destination);
    }
    try {
      UUID topicParentId = UUID.fromString(parts[3]);
      if (!topicParentId.equals(principal.userId())) {
        throw new MessageDeliveryException("Parent cannot subscribe to foreign alerts topic");
      }
    } catch (IllegalArgumentException e) {
      throw new MessageDeliveryException("Invalid parent id in topic: " + destination);
    }
  }
}
