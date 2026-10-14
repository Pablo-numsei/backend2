package com.itb.config;

import com.itb.service.CustomUserDetailsService;
import com.itb.service.JwtService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketAuthenticationInterceptor implements ChannelInterceptor {
    private static final Set<String> STAFF_ROLES = Set.of(
            "ROLE_ADMINISTRADOR", "ROLE_GARCOM", "ROLE_COZINHA");
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) {
            return message;
        }
        String sessionId = headers.getSessionId();
        if (headers.getCommand() == StompCommand.DISCONNECT) {
            if (sessionId != null) sessionTokens.remove(sessionId);
            return message;
        }
        if (headers.getCommand() == StompCommand.CONNECT) {
            String authorization = headers.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ") || sessionId == null) {
                throw denied();
            }
            String token = authorization.substring(7);
            UserDetails user = authenticate(token);
            headers.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            sessionTokens.put(sessionId, token);
            return message;
        }
        authenticate(sessionId == null ? null : sessionTokens.get(sessionId));
        if (headers.getCommand() == StompCommand.SUBSCRIBE) {
            if (!"/topic/orders".equals(headers.getDestination())) throw denied();
        } else if (headers.getCommand() != StompCommand.UNSUBSCRIBE) {
            // Server topics are read-only for clients, including authenticated staff.
            throw denied();
        }
        return message;
    }

    public Message<?> authorizeOutbound(Message<?> message) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
        if (headers.getMessageType() != SimpMessageType.MESSAGE) return message;
        try {
            authenticate(headers.getSessionId() == null ? null : sessionTokens.get(headers.getSessionId()));
            return message;
        } catch (AccessDeniedException ex) {
            // Expired tokens, disabled users and removed roles stop receiving events.
            return null;
        }
    }

    private UserDetails authenticate(String token) {
        try {
            if (token == null || !jwtService.isTokenValid(token)) throw denied();
            UserDetails user = userDetailsService.loadUserByUsername(jwtService.extractEmail(token));
            if (user.getAuthorities().stream().noneMatch(a -> STAFF_ROLES.contains(a.getAuthority()))) {
                throw denied();
            }
            return user;
        } catch (Exception ex) {
            throw denied();
        }
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Conexão de notificações não autorizada.");
    }
}
