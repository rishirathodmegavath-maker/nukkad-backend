package com.nukkad.messaging.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    public enum Type { TEXT, SHARED_POST }

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "conversation_id", nullable = false, columnDefinition = "CHAR(36)")
    private String conversationId;

    @Column(name = "sender_id", nullable = false, columnDefinition = "CHAR(36)")
    private String senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    @Builder.Default
    private Type messageType = Type.TEXT;

    @Column(name = "shared_post_id", columnDefinition = "CHAR(36)")
    private String sharedPostId;

    /** AES-256-GCM ciphertext, base64-encoded (IV || ciphertext). Never plaintext at rest. */
    @Column(name = "content_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String contentCiphertext;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
