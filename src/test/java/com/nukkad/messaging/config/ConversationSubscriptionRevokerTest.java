package com.nukkad.messaging.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationSubscriptionRevokerTest {

    @Mock private SimpUserRegistry userRegistry;
    @Mock private MessageChannel clientInboundChannel;

    private ConversationSubscriptionRevoker revoker() {
        return new ConversationSubscriptionRevoker(userRegistry, clientInboundChannel);
    }

    private static SimpSubscription subscription(String id, String destination) {
        SimpSubscription subscription = mock(SimpSubscription.class);
        lenient().when(subscription.getId()).thenReturn(id);
        when(subscription.getDestination()).thenReturn(destination);
        return subscription;
    }

    private static SimpSession session(String id, SimpSubscription... subscriptions) {
        SimpSession session = mock(SimpSession.class);
        lenient().when(session.getId()).thenReturn(id);
        when(session.getSubscriptions()).thenReturn(Set.of(subscriptions));
        return session;
    }

    @Test
    void revokesOnlyTheMessageAndReadReceiptSubscriptionsOfThatConversationAcrossEveryOpenSocket() {
        // Built up front: creating a mock inside another stubbing call's argument is what Mockito calls
        // UnfinishedStubbing.
        SimpSession tab1 = session("tab-1",
                subscription("sub-messages", "/topic/conversations/conv1"),
                subscription("sub-read", "/topic/conversations/conv1/read"),
                subscription("sub-other-conv", "/topic/conversations/conv2"),
                subscription("sub-lookalike", "/topic/conversations/conv10"),
                subscription("sub-inbox", "/topic/users/carol/conversations"));
        SimpSession tab2 = session("tab-2", subscription("sub-messages-2", "/topic/conversations/conv1"));
        SimpUser carol = mock(SimpUser.class);
        when(userRegistry.getUser("carol")).thenReturn(carol);
        when(carol.getSessions()).thenReturn(Set.of(tab1, tab2));

        revoker().revoke("carol", "conv1");

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(clientInboundChannel, times(3)).send(sent.capture());
        assertThat(sent.getAllValues()).allSatisfy(frame ->
                assertThat(SimpMessageHeaderAccessor.getMessageType(frame.getHeaders())).isEqualTo(SimpMessageType.UNSUBSCRIBE));
        @SuppressWarnings("unchecked")
        List<String> revoked = ((List<Message<?>>) (List<?>) sent.getAllValues()).stream()
                .map(frame -> SimpMessageHeaderAccessor.getSessionId(frame.getHeaders()) + "/" + SimpMessageHeaderAccessor.getSubscriptionId(frame.getHeaders()))
                .toList();
        // Her other conversation, a conversation whose id merely starts with "conv1", and her own inbox are untouched.
        assertThat(revoked).containsExactlyInAnyOrder("tab-1/sub-messages", "tab-1/sub-read", "tab-2/sub-messages-2");
    }

    @Test
    void isANoOpForAUserWhoIsNotConnected() {
        when(userRegistry.getUser("carol")).thenReturn(null);

        revoker().revoke("carol", "conv1");

        verifyNoInteractions(clientInboundChannel);
    }

    @Test
    void neverTouchesAnotherUsersSubscriptionsToTheSameConversation() {
        when(userRegistry.getUser("carol")).thenReturn(null);

        revoker().revoke("carol", "conv1");

        // Only carol is looked up — the remaining members' live subscriptions are never enumerated or cut.
        verify(userRegistry).getUser("carol");
        verify(userRegistry, never()).getUsers();
        verify(clientInboundChannel, never()).send(any());
    }

    @Test
    void onlyTheExactMessageAndReadReceiptTopicsOfTheConversationCount() {
        assertThat(ConversationSubscriptionRevoker.isConversationTopic("/topic/conversations/conv1", "conv1")).isTrue();
        assertThat(ConversationSubscriptionRevoker.isConversationTopic("/topic/conversations/conv1/read", "conv1")).isTrue();
        assertThat(ConversationSubscriptionRevoker.isConversationTopic("/topic/conversations/conv10", "conv1")).isFalse();
        assertThat(ConversationSubscriptionRevoker.isConversationTopic("/topic/conversations/conv1/extra", "conv1")).isFalse();
        assertThat(ConversationSubscriptionRevoker.isConversationTopic("/topic/users/conv1/conversations", "conv1")).isFalse();
        assertThat(ConversationSubscriptionRevoker.isConversationTopic(null, "conv1")).isFalse();
    }
}
