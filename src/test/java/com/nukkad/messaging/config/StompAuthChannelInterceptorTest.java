package com.nukkad.messaging.config;

import com.nukkad.messaging.entity.Conversation;
import com.nukkad.messaging.repository.ConversationParticipantRepository;
import com.nukkad.messaging.repository.ConversationRepository;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.security.CorsProperties;
import com.nukkad.security.JwtService;
import com.nukkad.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Drives the REAL {@link WebSocketConfig} (real simple broker, real inbound/outbound channels) with the
 * REAL {@link StompAuthChannelInterceptor} — only the JWT parsing and the two repositories are stubbed —
 * so these tests prove what a hostile STOMP client can actually do, not what the interceptor's code
 * looks like it does. STOMP frames are injected straight onto the client-inbound channel, exactly where
 * Spring's own STOMP handler puts frames it decodes off a WebSocket.
 *
 * <p>The broker is a plain in-memory simple broker whose subscription registry treats a SUBSCRIBE
 * destination as an Ant-style pattern and which fans out a client's SEND to {@code /topic/**} to every
 * subscriber — so an interceptor that only prefix-checks {@code /topic/conversations/} and
 * {@code /topic/users/} leaves both a wildcard-subscribe and a message-forgery hole open. Every test
 * below that says "denied" would fail against that version.
 */
@SpringJUnitWebConfig
class StompAuthChannelInterceptorTest {

    private static final String ALICE = "a11ce000-0000-4000-8000-000000000001";
    private static final String BOB = "b0b00000-0000-4000-8000-000000000002";
    private static final String EVE = "e0e00000-0000-4000-8000-000000000003";
    private static final String CAROL = "ca201000-0000-4000-8000-000000000004";

    private static final String DIRECT_CONV = "c0000000-0000-4000-8000-0000000000ab";
    private static final String OTHER_DIRECT_CONV = "c0000000-0000-4000-8000-0000000000cd";
    private static final String GROUP_CONV = "c0000000-0000-4000-8000-0000000000ef";

    /** {@link ConversationSubscriptionRevoker} is imported as a real bean, so its constructor wiring (the
     * {@code SimpUserRegistry} and the {@code clientInboundChannel} qualifier) is resolved by Spring exactly as in
     * the running app. */
    @Configuration
    @Import({WebSocketConfig.class, ConversationSubscriptionRevoker.class})
    static class Config {
        @Bean CorsProperties corsProperties() { return new CorsProperties("http://localhost:5173"); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean ConversationRepository conversationRepository() { return mock(ConversationRepository.class); }
        @Bean ConversationParticipantRepository conversationParticipantRepository() { return mock(ConversationParticipantRepository.class); }

        @Bean
        StompAuthChannelInterceptor stompAuthChannelInterceptor(JwtService jwtService, UserRepository users,
                                                                 ConversationRepository conversations,
                                                                 ConversationParticipantRepository participants) {
            return new StompAuthChannelInterceptor(jwtService, users, conversations, participants);
        }
    }

    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ConversationParticipantRepository participantRepository;
    @Autowired @Qualifier("clientInboundChannel") private MessageChannel clientInbound;
    @Autowired @Qualifier("clientOutboundChannel") private SubscribableChannel clientOutbound;
    @Autowired private SimpMessagingTemplate template;
    @Autowired private ConversationSubscriptionRevoker revoker;
    @Autowired private org.springframework.context.ApplicationEventPublisher events;

    private final List<Message<?>> delivered = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        reset(jwtService, userRepository, conversationRepository, participantRepository);
        delivered.clear();
        // The outbound channel is shared across tests (one Spring context) — a fresh capturing handler each time.
        clientOutbound.subscribe(delivered::add);

        stubUser("tok-alice", ALICE);
        stubUser("tok-bob", BOB);
        stubUser("tok-eve", EVE);
        stubUser("tok-carol", CAROL);

        Conversation direct = Conversation.builder().id(DIRECT_CONV).userAId(ALICE).userBId(BOB).build();
        Conversation otherDirect = Conversation.builder().id(OTHER_DIRECT_CONV).userAId(BOB).userBId(CAROL).build();
        Conversation group = Conversation.builder().id(GROUP_CONV).conversationType(Conversation.Type.GROUP).groupName("g").build();
        when(conversationRepository.findById(anyString())).thenReturn(Optional.empty());
        when(conversationRepository.findById(DIRECT_CONV)).thenReturn(Optional.of(direct));
        when(conversationRepository.findById(OTHER_DIRECT_CONV)).thenReturn(Optional.of(otherDirect));
        when(conversationRepository.findById(GROUP_CONV)).thenReturn(Optional.of(group));
        when(participantRepository.existsByConversationIdAndUserIdAndDeletedAtIsNull(GROUP_CONV, CAROL)).thenReturn(true);
    }

    private void stubUser(String token, String userId) {
        Claims claims = mock(Claims.class);
        when(jwtService.parseAndValidate(token)).thenReturn(claims);
        when(jwtService.isAdminScope(claims)).thenReturn(false);
        when(jwtService.toAuthenticatedUser(claims)).thenReturn(new AuthenticatedUser(userId, userId + "@test.local", Set.of("USER"), 0));
        when(userRepository.findTokenVersionById(userId)).thenReturn(Optional.of(0));
    }

    // ---- frame helpers -------------------------------------------------------------------------------------

    private void connect(String sessionId, String authorizationHeader) {
        StompHeaderAccessor h = StompHeaderAccessor.create(StompCommand.CONNECT);
        h.setSessionId(sessionId);
        if (authorizationHeader != null) h.addNativeHeader("Authorization", authorizationHeader);
        h.setLeaveMutable(true);
        clientInbound.send(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()));
    }

    private final Set<String> connectedSessions = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A real client always CONNECTs first, and Spring's simple broker only delivers to sessions it saw a
     * CONNECT for — so without this no test here would ever receive anything, legitimate or not. */
    private void ensureConnected(String sessionId, String userId) {
        if (userId == null || !connectedSessions.add(sessionId)) return;
        String token = userId.equals(ALICE) ? "tok-alice" : userId.equals(BOB) ? "tok-bob" : userId.equals(EVE) ? "tok-eve" : "tok-carol";
        connect(sessionId, "Bearer " + token);
    }

    /** {@code userId} stands in for what Spring's STOMP handler re-attaches from the authenticated CONNECT. */
    private void subscribe(String sessionId, String userId, String destination) {
        ensureConnected(sessionId, userId);
        StompHeaderAccessor h = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        h.setSessionId(sessionId);
        h.setSubscriptionId("sub-" + destination.hashCode());
        h.setDestination(destination);
        if (userId != null) h.setUser(new StompPrincipal(userId));
        h.setLeaveMutable(true);
        clientInbound.send(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()));
    }

    private void clientSend(String sessionId, String userId, String destination, String body) {
        ensureConnected(sessionId, userId);
        StompHeaderAccessor h = StompHeaderAccessor.create(StompCommand.SEND);
        h.setSessionId(sessionId);
        h.setDestination(destination);
        if (userId != null) h.setUser(new StompPrincipal(userId));
        h.setLeaveMutable(true);
        clientInbound.send(MessageBuilder.createMessage(body.getBytes(StandardCharsets.UTF_8), h.getMessageHeaders()));
    }

    private static String bodyOf(Message<?> m) {
        Object payload = m.getPayload();
        String text = payload instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(payload);
        // Depending on the converter chain a String payload can arrive JSON-quoted; the text inside is what matters.
        return text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"") ? text.substring(1, text.length() - 1) : text;
    }

    private boolean receivedBy(String sessionId, String body) {
        return delivered.stream().anyMatch(m -> sessionId.equals(SimpMessageHeaderAccessor.getSessionId(m.getHeaders()))
                && bodyOf(m).equals(body));
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 4000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("condition not met within 4s");
            Thread.sleep(20);
        }
    }

    /** SUBSCRIBE is registered asynchronously on the broker's executor, so a broadcast sent the instant
     * {@code subscribe(..)} returns can be lost. Re-broadcasting a probe until the subscriber actually gets
     * one is what proves the subscription is live before anything else is asserted. */
    private void broadcastUntilReceived(String sessionId, String destination) throws InterruptedException {
        String probe = "probe-" + System.nanoTime();
        long deadline = System.currentTimeMillis() + 4000;
        while (!receivedBy(sessionId, probe)) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("session " + sessionId + " never received a broadcast on " + destination
                        + "; delivered so far: " + delivered.stream()
                        .map(m -> SimpMessageHeaderAccessor.getSessionId(m.getHeaders()) + "<-" + bodyOf(m)).toList());
            }
            template.convertAndSend(destination, probe);
            Thread.sleep(40);
        }
    }

    /** Proves the (asynchronous, multi-threaded) broker has finished processing everything queued before
     * this call, so a following "was NOT delivered" assertion is meaningful rather than merely early. */
    private void drainThrough(String legitSessionId, String legitDestination) throws InterruptedException {
        broadcastUntilReceived(legitSessionId, legitDestination);
        Thread.sleep(300);
    }

    // ---- CONNECT ---------------------------------------------------------------------------------------------

    @Test
    void connectWithoutABearerTokenIsRejected() {
        assertThatThrownBy(() -> connect("s-anon", null)).hasRootCauseInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> connect("s-anon", "Basic abc")).hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithAnInvalidOrExpiredTokenIsRejected() {
        when(jwtService.parseAndValidate("tok-bad")).thenThrow(new JwtException("expired"));
        assertThatThrownBy(() -> connect("s-bad", "Bearer tok-bad")).hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aTokenWhoseVersionWasBumpedBySuspensionDisableOrLogoutEverywhereCannotConnect() {
        // Same rule JwtAuthenticationFilter applies to REST: the token is still cryptographically valid and
        // unexpired, but the account's current tokenVersion no longer matches the one embedded in it.
        when(userRepository.findTokenVersionById(EVE)).thenReturn(Optional.of(1));
        assertThatThrownBy(() -> connect("s-eve", "Bearer tok-eve")).hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aTokenForADeletedAccountCannotConnect() {
        when(userRepository.findTokenVersionById(EVE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> connect("s-eve", "Bearer tok-eve")).hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anAdminScopedTokenCannotUseMessaging() {
        Claims adminClaims = mock(Claims.class);
        when(jwtService.parseAndValidate("tok-admin")).thenReturn(adminClaims);
        when(jwtService.isAdminScope(adminClaims)).thenReturn(true);
        assertThatThrownBy(() -> connect("s-admin", "Bearer tok-admin")).hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    // ---- SUBSCRIBE: the legitimate paths must still work end to end -----------------------------------------

    @Test
    void aParticipantReceivesTheirOwnConversationsMessagesAndReadReceipts() throws Exception {
        subscribe("s-alice", ALICE, "/topic/conversations/" + DIRECT_CONV);
        subscribe("s-alice", ALICE, "/topic/conversations/" + DIRECT_CONV + "/read");
        subscribe("s-alice", ALICE, "/topic/users/" + ALICE + "/conversations");

        broadcastUntilReceived("s-alice", "/topic/conversations/" + DIRECT_CONV);
        broadcastUntilReceived("s-alice", "/topic/conversations/" + DIRECT_CONV + "/read");
        broadcastUntilReceived("s-alice", "/topic/users/" + ALICE + "/conversations");
    }

    @Test
    void aCurrentGroupMemberMaySubscribeToTheGroupTopic() throws Exception {
        subscribe("s-carol", CAROL, "/topic/conversations/" + GROUP_CONV);
        broadcastUntilReceived("s-carol", "/topic/conversations/" + GROUP_CONV);
    }

    // ---- SUBSCRIBE: conversation IDOR ------------------------------------------------------------------------

    @Test
    void aNonParticipantCannotSubscribeToAnotherPairsConversationOrItsReadReceipts() {
        assertThatThrownBy(() -> subscribe("s-eve", EVE, "/topic/conversations/" + DIRECT_CONV))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> subscribe("s-eve", EVE, "/topic/conversations/" + DIRECT_CONV + "/read"))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aUserCannotSwapInAnotherConversationIdTheyAreNotPartOf() {
        // Bob is in DIRECT_CONV (with Alice) and OTHER_DIRECT_CONV (with Carol); Alice is only in the first.
        subscribe("s-alice", ALICE, "/topic/conversations/" + DIRECT_CONV);
        assertThatThrownBy(() -> subscribe("s-alice", ALICE, "/topic/conversations/" + OTHER_DIRECT_CONV))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theUnsubscribeFrameUsedToCutOffARemovedMemberReallyStopsDeliveryOnTheRealBroker() throws Exception {
        String destination = "/topic/conversations/" + GROUP_CONV;
        // Two open sockets for the same member — only the one being revoked may go quiet.
        subscribe("s-carol-tab1", CAROL, destination);
        subscribe("s-carol-tab2", CAROL, destination);
        broadcastUntilReceived("s-carol-tab1", destination);
        broadcastUntilReceived("s-carol-tab2", destination);

        clientInbound.send(ConversationSubscriptionRevoker.unsubscribeFrame("s-carol-tab1", "sub-" + destination.hashCode()));
        Thread.sleep(300);

        String afterRevoke = "sent-after-revoke-" + System.nanoTime();
        template.convertAndSend(destination, afterRevoke);
        await(() -> receivedBy("s-carol-tab2", afterRevoke));   // the untouched socket still gets it
        Thread.sleep(300);
        assertThat(receivedBy("s-carol-tab1", afterRevoke)).as("the revoked subscription must go quiet").isFalse();
    }

    @Test
    void revokingAMemberFindsTheirLiveSubscriptionsInSpringsRealUserRegistryAndCutsThemOffWhileOthersKeepReceiving() throws Exception {
        String destination = "/topic/conversations/" + GROUP_CONV;
        subscribe("s-carol", CAROL, destination);
        subscribe("s-carol-other-user-tab", CAROL, destination);
        broadcastUntilReceived("s-carol", destination);
        broadcastUntilReceived("s-carol-other-user-tab", destination);

        // Spring's STOMP handler publishes these as a real socket connects and subscribes (that's what fills the user
        // registry). This harness has no WebSocket, so it publishes the same events itself.
        publishSessionEvents("s-carol", CAROL, "sub-" + destination.hashCode(), destination);

        revoker.revoke(CAROL, GROUP_CONV);
        Thread.sleep(300);

        String afterRevoke = "sent-after-revoke-" + System.nanoTime();
        template.convertAndSend(destination, afterRevoke);
        // Only the session Spring's registry knew about was cut off; the other tab (never registered) still receives,
        // which also proves the harness is delivering at all.
        await(() -> receivedBy("s-carol-other-user-tab", afterRevoke));
        Thread.sleep(300);
        assertThat(receivedBy("s-carol", afterRevoke)).as("revoked through the real registry").isFalse();
    }

    private void publishSessionEvents(String sessionId, String userId, String subscriptionId, String destination) {
        StompPrincipal principal = new StompPrincipal(userId);
        org.springframework.messaging.simp.SimpMessageHeaderAccessor connected =
                org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.CONNECT_ACK);
        connected.setSessionId(sessionId);
        connected.setUser(principal);
        connected.setLeaveMutable(true);
        events.publishEvent(new org.springframework.web.socket.messaging.SessionConnectedEvent(
                this, MessageBuilder.createMessage(new byte[0], connected.getMessageHeaders()), principal));

        org.springframework.messaging.simp.SimpMessageHeaderAccessor subscribed =
                org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.SUBSCRIBE);
        subscribed.setSessionId(sessionId);
        subscribed.setSubscriptionId(subscriptionId);
        subscribed.setDestination(destination);
        subscribed.setUser(principal);
        subscribed.setLeaveMutable(true);
        events.publishEvent(new org.springframework.web.socket.messaging.SessionSubscribeEvent(
                this, MessageBuilder.createMessage(new byte[0], subscribed.getMessageHeaders()), principal));
    }

    @Test
    void aFormerGroupMemberCannotSubscribeAndAnUnknownConversationIsDeniedTheSameWay() {
        // EVE has no active participant row in GROUP_CONV; the id below does not exist at all.
        Throwable forgottenMember = org.junit.jupiter.api.Assertions.assertThrows(MessageDeliveryException.class,
                () -> subscribe("s-eve", EVE, "/topic/conversations/" + GROUP_CONV));
        Throwable unknownConversation = org.junit.jupiter.api.Assertions.assertThrows(MessageDeliveryException.class,
                () -> subscribe("s-eve", EVE, "/topic/conversations/c0000000-0000-4000-8000-00000000dead"));
        // Same exception type and same message: nothing here tells a probing client which ids exist.
        assertThat(rootCause(forgottenMember)).isInstanceOf(AccessDeniedException.class);
        assertThat(rootCause(unknownConversation)).isInstanceOf(AccessDeniedException.class);
        assertThat(rootCause(unknownConversation).getMessage()).isEqualTo(rootCause(forgottenMember).getMessage());
    }

    @Test
    void aUserCannotSubscribeToAnotherUsersPersonalTopic() {
        assertThatThrownBy(() -> subscribe("s-eve", EVE, "/topic/users/" + ALICE + "/conversations"))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anUnauthenticatedSubscribeIsRejected() {
        assertThatThrownBy(() -> subscribe("s-anon", null, "/topic/conversations/" + DIRECT_CONV))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    // ---- SUBSCRIBE: wildcard / pattern bypasses --------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "/topic/**",
            "/topic/*",
            "/topic/*/*",
            "/topic/*/**",
            "/topic/conversations/*",
            "/topic/conversations/**",
            "/topic/conversations/*/read",
            "/topic/conversations/c0000000-0000-4000-8000-0000000000ab/*",
            "/topic/conversations/c0000000-0000-4000-8000-0000000000ab/**",
            "/topic/conversations/c0000000-0000-4000-8000-0000000000?b",
            "/topic/conversations/{id}",
            "/topic/users/*/conversations",
            "/topic/users/**",
            "/topic/*/a11ce000-0000-4000-8000-000000000001/conversations",
    })
    void wildcardSubscriptionsAreRejectedSoNoOneCanEavesdropOnEveryConversation(String destination) {
        assertThatThrownBy(() -> subscribe("s-eve", EVE, destination)).hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aWildcardSubscriptionThatIsRejectedNeverReceivesAnyone_sMessages() throws Exception {
        subscribe("s-alice", ALICE, "/topic/conversations/" + DIRECT_CONV);
        subscribe("s-alice", ALICE, "/topic/users/" + ALICE + "/conversations");
        try {
            subscribe("s-eve", EVE, "/topic/**");
        } catch (RuntimeException expected) {
            // rejected — the assertion that matters is that she never receives anything below
        }
        Thread.sleep(300); // let a (wrongly) accepted subscription finish registering
        broadcastUntilReceived("s-alice", "/topic/conversations/" + DIRECT_CONV);
        broadcastUntilReceived("s-alice", "/topic/users/" + ALICE + "/conversations");
        drainThrough("s-alice", "/topic/conversations/" + DIRECT_CONV);
        assertThat(delivered).noneMatch(m -> "s-eve".equals(SimpMessageHeaderAccessor.getSessionId(m.getHeaders())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/topic/conversations/c0000000-0000-4000-8000-0000000000ab/extra",
            "/topic/conversations/c0000000-0000-4000-8000-0000000000ab/read/more",
            "/topic/conversations//read",
            "/topic/conversations/../users/a11ce000-0000-4000-8000-000000000001/conversations",
            "/topic/users/a11ce000-0000-4000-8000-000000000001/conversations/extra",
            "/topic/users/a11ce000-0000-4000-8000-000000000001",
            "/topic/conversations",
            "/topic/conversations/",
            "/topic/anything-else",
            "/topic",
            "/queue/whatever",
            "/user/queue/whatever",
            "/app/anything",
    })
    void anyDestinationOutsideTheThreeKnownShapesIsDefaultDenied(String destination) {
        assertThatThrownBy(() -> subscribe("s-alice", ALICE, destination)).hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    // ---- SEND: a client must never be able to publish onto a broker topic ------------------------------------

    @Test
    void aClientCannotForgeAMessageOntoAConversationTopicItIsNotPartOf() throws Exception {
        subscribe("s-alice", ALICE, "/topic/conversations/" + DIRECT_CONV);
        broadcastUntilReceived("s-alice", "/topic/conversations/" + DIRECT_CONV);
        assertThatThrownBy(() -> clientSend("s-eve", EVE, "/topic/conversations/" + DIRECT_CONV, "forged by eve"))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
        drainThrough("s-alice", "/topic/conversations/" + DIRECT_CONV);
        assertThat(receivedBy("s-alice", "forged by eve")).isFalse();
    }

    @Test
    void evenAParticipantCannotPublishDirectlyToTheBrokerBecauseOnlyTheServerBroadcastsMessages() throws Exception {
        // Bob IS a participant — but a real message is created via the REST API, persisted, and only THEN
        // broadcast by the server. A client-published frame would be an unpersisted, unvalidated forgery
        // (any senderId, any attachment URL) delivered to the other participant as if it came from the server.
        subscribe("s-alice", ALICE, "/topic/conversations/" + DIRECT_CONV);
        broadcastUntilReceived("s-alice", "/topic/conversations/" + DIRECT_CONV);
        assertThatThrownBy(() -> clientSend("s-bob", BOB, "/topic/conversations/" + DIRECT_CONV, "forged by bob"))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
        drainThrough("s-alice", "/topic/conversations/" + DIRECT_CONV);
        assertThat(receivedBy("s-alice", "forged by bob")).isFalse();
    }

    @Test
    void aClientCannotForgeAPersonalSidebarUpdateForAnotherUser() throws Exception {
        subscribe("s-alice", ALICE, "/topic/users/" + ALICE + "/conversations");
        broadcastUntilReceived("s-alice", "/topic/users/" + ALICE + "/conversations");
        assertThatThrownBy(() -> clientSend("s-eve", EVE, "/topic/users/" + ALICE + "/conversations", "phish"))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
        drainThrough("s-alice", "/topic/users/" + ALICE + "/conversations");
        assertThat(receivedBy("s-alice", "phish")).isFalse();
    }

    @Test
    void anyClientSendIsRejectedIncludingApplicationDestinationsBecauseThereAreNoClientCallableHandlers() {
        assertThatThrownBy(() -> clientSend("s-alice", ALICE, "/app/anything", "x")).hasRootCauseInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> clientSend("s-alice", ALICE, "/topic/anything", "x")).hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anUnauthenticatedSendIsRejected() {
        assertThatThrownBy(() -> clientSend("s-anon", null, "/topic/conversations/" + DIRECT_CONV, "x"))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t;
    }
}
