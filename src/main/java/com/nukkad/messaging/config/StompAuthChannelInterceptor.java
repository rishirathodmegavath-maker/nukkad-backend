package com.nukkad.messaging.config;

import com.nukkad.messaging.repository.ConversationRepository;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Authenticates STOMP CONNECT frames with the same JWT used for REST calls, and authorizes
 * SUBSCRIBE frames so a socket can only listen on conversations it is actually a participant in.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String CONVERSATIONS_PREFIX = "/topic/conversations/";
    private static final String USERS_PREFIX = "/topic/users/";

    private final JwtService jwtService;
    private final ConversationRepository conversationRepository;

    public StompAuthChannelInterceptor(JwtService jwtService, ConversationRepository conversationRepository) {
        this.jwtService = jwtService;
        this.conversationRepository = conversationRepository;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new AccessDeniedException("Missing bearer token on STOMP CONNECT");
            }
            try {
                AuthenticatedUser user = jwtService.toAuthenticatedUser(jwtService.parseAndValidate(authHeader.substring(7)));
                accessor.setUser(new StompPrincipal(user.id()));
            } catch (JwtException e) {
                throw new AccessDeniedException("Invalid or expired token");
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal principal = accessor.getUser();
            String userId = principal != null ? principal.getName() : null;
            String destination = accessor.getDestination();
            if (userId == null || destination == null) {
                throw new AccessDeniedException("Unauthenticated subscription");
            }
            if (destination.startsWith(CONVERSATIONS_PREFIX)) {
                String rest = destination.substring(CONVERSATIONS_PREFIX.length());
                String conversationId = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
                boolean allowed = conversationRepository.findById(conversationId)
                        .map(c -> c.hasParticipant(userId))
                        .orElse(false);
                if (!allowed) throw new AccessDeniedException("Not a participant in this conversation");
            } else if (destination.startsWith(USERS_PREFIX)) {
                String rest = destination.substring(USERS_PREFIX.length());
                String targetUserId = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
                if (!userId.equals(targetUserId)) {
                    throw new AccessDeniedException("Cannot subscribe to another user's channel");
                }
            }
        }
        return message;
    }
}
