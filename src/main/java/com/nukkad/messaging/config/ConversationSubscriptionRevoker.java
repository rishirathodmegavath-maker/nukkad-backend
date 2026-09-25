package com.nukkad.messaging.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * {@link StompAuthChannelInterceptor} decides who may SUBSCRIBE, but a subscription then lives as long as
 * the socket does. Without this, someone removed from a group (or who leaves it) keeps receiving that
 * group's live messages — plaintext, with freshly presigned attachment URLs — until their tab happens to
 * reconnect, even though every REST call about that group already refuses them.
 *
 * <p>Membership changes call {@link #revoke}, which finds the user's live subscriptions on that one
 * conversation's topics and unsubscribes them on the broker (the same UNSUBSCRIBE frame a client would send
 * itself). Any later re-subscribe attempt is refused by the interceptor's participant check.
 */
@Component
public class ConversationSubscriptionRevoker {

    private final SimpUserRegistry userRegistry;
    private final MessageChannel clientInboundChannel;

    public ConversationSubscriptionRevoker(SimpUserRegistry userRegistry,
                                            @Qualifier("clientInboundChannel") MessageChannel clientInboundChannel) {
        this.userRegistry = userRegistry;
        this.clientInboundChannel = clientInboundChannel;
    }

    /** Unsubscribes every live subscription {@code userId} holds on {@code conversationId}'s message and
     * read-receipt topics, across all of their open sockets. A no-op if they aren't connected. */
    public void revoke(String userId, String conversationId) {
        SimpUser user = userRegistry.getUser(userId);
        if (user == null) return;
        for (SimpSession session : user.getSessions()) {
            for (SimpSubscription subscription : session.getSubscriptions()) {
                if (isConversationTopic(subscription.getDestination(), conversationId)) {
                    clientInboundChannel.send(unsubscribeFrame(session.getId(), subscription.getId()));
                }
            }
        }
    }

    static boolean isConversationTopic(String destination, String conversationId) {
        if (destination == null) return false;
        String messages = "/topic/conversations/" + conversationId;
        return destination.equals(messages) || destination.equals(messages + "/read");
    }

    static Message<byte[]> unsubscribeFrame(String sessionId, String subscriptionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
