package com.nukkad.messaging.config;

import com.nukkad.messaging.entity.Conversation;
import com.nukkad.messaging.repository.ConversationParticipantRepository;
import com.nukkad.messaging.repository.ConversationRepository;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.security.JwtService;
import com.nukkad.user.repository.UserRepository;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The only authorization gate for real-time messaging: {@code /ws/**} is {@code permitAll} at the HTTP
 * layer (a STOMP client authenticates in-band, on CONNECT), so nothing else stands between an
 * authenticated socket and the in-memory broker.
 *
 * <ul>
 *   <li><b>CONNECT</b> is authenticated with the same JWT as REST <em>and</em> the same token-version
 *       check {@code JwtAuthenticationFilter} applies, so a suspended/disabled/logged-out-everywhere
 *       account can't open a socket on a token that is still within its expiry.</li>
 *   <li><b>SUBSCRIBE</b> is default-deny. The destination must match one of exactly three shapes
 *       (below) — a single path segment of {@code [A-Za-z0-9_-]} per id. That rules out every wildcard
 *       ({@code *}, {@code **}, {@code ?}, {@code {x}}): Spring's simple broker treats a subscribe
 *       destination as an Ant pattern, so an unrestricted {@code /topic/**} (or {@code /topic/*}{@code /*})
 *       would otherwise deliver <em>every</em> user's private messages to whoever asked.</li>
 *   <li><b>Client-originated publishing is refused outright</b> ({@code SEND}, and the server-only
 *       {@code MESSAGE}/{@code ACK}/… frames a hostile client could also craft). The simple broker fans a
 *       client's frame addressed to {@code /topic/**} out to every subscriber exactly as if the server had
 *       sent it, which would let anyone forge a message, sender or attachment link into someone else's
 *       chat. Nothing legitimate needs it: real messages are created over REST, persisted, and only then
 *       broadcast by the server (which never passes through this inbound interceptor).</li>
 * </ul>
 *
 * Every denial carries the same generic message, so a probing client can't tell an id that doesn't exist
 * from one it merely isn't allowed into.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String SUBSCRIPTION_DENIED = "Subscription not permitted";

    /** {@code /topic/conversations/{id}} (messages) and {@code /topic/conversations/{id}/read} (read receipts). */
    private static final Pattern CONVERSATION_TOPIC = Pattern.compile("^/topic/conversations/([A-Za-z0-9_-]{1,64})(?:/read)?$");
    /** {@code /topic/users/{id}/conversations}: a user's own inbox/sidebar updates. */
    private static final Pattern USER_TOPIC = Pattern.compile("^/topic/users/([A-Za-z0-9_-]{1,64})/conversations$");

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;

    public StompAuthChannelInterceptor(JwtService jwtService, UserRepository userRepository,
                                        ConversationRepository conversationRepository,
                                        ConversationParticipantRepository conversationParticipantRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message; // not a client STOMP frame (server-side/internal message)

        StompCommand command = accessor.getCommand();
        if (command == null) return message; // heartbeat

        switch (command) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case UNSUBSCRIBE, DISCONNECT -> { /* only ever affects the caller's own session */ }
            default -> throw new AccessDeniedException("Clients cannot send " + command + " frames");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("Missing bearer token on STOMP CONNECT");
        }
        try {
            var claims = jwtService.parseAndValidate(authHeader.substring(7));
            if (jwtService.isAdminScope(claims)) {
                throw new AccessDeniedException("Admin sessions cannot use messaging");
            }
            AuthenticatedUser user = jwtService.toAuthenticatedUser(claims);
            // Same fail-closed check as JwtAuthenticationFilter: a missing row (deleted account) or a version
            // that no longer matches the one embedded in the token (suspension/disable/logout-all) is rejected.
            Integer currentTokenVersion = userRepository.findTokenVersionById(user.id()).orElse(null);
            if (currentTokenVersion == null || currentTokenVersion != user.tokenVersion()) {
                throw new AccessDeniedException("Invalid or expired token");
            }
            accessor.setUser(new StompPrincipal(user.id()));
        } catch (JwtException | IllegalArgumentException e) {
            throw new AccessDeniedException("Invalid or expired token");
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        String userId = principal != null ? principal.getName() : null;
        String destination = accessor.getDestination();
        if (userId == null || destination == null) {
            throw new AccessDeniedException("Unauthenticated subscription");
        }

        Matcher conversation = CONVERSATION_TOPIC.matcher(destination);
        if (conversation.matches()) {
            if (!isParticipant(conversation.group(1), userId)) throw new AccessDeniedException(SUBSCRIPTION_DENIED);
            return;
        }
        Matcher userTopic = USER_TOPIC.matcher(destination);
        if (userTopic.matches() && userId.equals(userTopic.group(1))) {
            return;
        }
        throw new AccessDeniedException(SUBSCRIPTION_DENIED);
    }

    private boolean isParticipant(String conversationId, String userId) {
        return conversationRepository.findById(conversationId)
                .map(c -> c.getConversationType() == Conversation.Type.GROUP
                        ? conversationParticipantRepository.existsByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                        : c.hasParticipant(userId))
                .orElse(false);
    }
}
