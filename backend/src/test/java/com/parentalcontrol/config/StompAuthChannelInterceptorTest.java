package com.parentalcontrol.config;

import com.parentalcontrol.common.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompAuthChannelInterceptorTest {

    private StompAuthChannelInterceptor interceptor;
    private StompPrincipal parentPrincipal;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(null, null, null);
        parentPrincipal = new StompPrincipal(
                UUID.randomUUID(), "parent@example.com", Role.ROLE_PARENT, null);
    }

    @Test
    void rejectsSubscribeToUnknownTopic() {
        Message<byte[]> message = stompMessage(StompCommand.SUBSCRIBE, "/topic/admin/secrets");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Forbidden STOMP destination");
    }

    @Test
    void rejectsSendWithNullDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setUser(auth(parentPrincipal));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Forbidden STOMP destination");
    }

    private Message<byte[]> stompMessage(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(auth(parentPrincipal));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static UsernamePasswordAuthenticationToken auth(StompPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
