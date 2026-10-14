package com.itb.config;

import com.itb.service.CustomUserDetailsService;
import com.itb.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketAuthenticationInterceptorTest {
    private JwtService jwt;
    private CustomUserDetailsService users;
    private WebSocketAuthenticationInterceptor interceptor;

    @BeforeEach
    void setup() {
        jwt = mock(JwtService.class);
        users = mock(CustomUserDetailsService.class);
        interceptor = new WebSocketAuthenticationInterceptor(jwt, users);
        when(jwt.isTokenValid("staff-token")).thenReturn(true);
        when(jwt.extractEmail("staff-token")).thenReturn("staff@example.test");
        when(users.loadUserByUsername("staff@example.test"))
                .thenReturn(User.withUsername("staff@example.test").password("hash").roles("GARCOM").build());
    }

    @Test
    void anonymousConnectionIsDenied() {
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null), null));
    }

    @Test
    void authenticatedStaffCanSubscribeOnlyToOrderUpdates() {
        connect();
        assertNotNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/orders", null), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/other", null), null));
    }

    @Test
    void staffCannotPublishFakeBrokerEvents() {
        connect();
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, "/topic/orders", null), null));
    }

    @Test
    void revokedOrExpiredTokenStopsOutboundUpdates() {
        connect();
        assertNotNull(interceptor.authorizeOutbound(outbound()));
        when(jwt.isTokenValid("staff-token")).thenReturn(false);
        assertNull(interceptor.authorizeOutbound(outbound()));
    }

    @Test
    void disabledUserStopsOutboundUpdates() {
        connect();
        when(users.loadUserByUsername("staff@example.test"))
                .thenThrow(new IllegalArgumentException("inactive"));
        assertNull(interceptor.authorizeOutbound(outbound()));
    }

    @Test
    void customerRoleCannotConnect() {
        when(users.loadUserByUsername("staff@example.test"))
                .thenReturn(User.withUsername("staff@example.test").password("hash").roles("CLIENTE").build());
        assertThrows(AccessDeniedException.class, this::connect);
    }

    @Test
    void disconnectRemovesAuthorization() {
        connect();
        interceptor.preSend(frame(StompCommand.DISCONNECT, null, null), null);
        assertNull(interceptor.authorizeOutbound(outbound()));
    }

    private void connect() {
        interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer staff-token"), null);
    }

    private Message<byte[]> frame(StompCommand command, String destination, String authorization) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setSessionId("session-1");
        if (destination != null) headers.setDestination(destination);
        if (authorization != null) headers.setNativeHeader("Authorization", authorization);
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private Message<byte[]> outbound() {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("session-1");
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
