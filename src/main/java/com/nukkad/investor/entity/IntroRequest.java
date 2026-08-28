package com.nukkad.investor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "intro_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntroRequest {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "requester_id", nullable = false, columnDefinition = "CHAR(36)")
    private String requesterId;

    @Column(name = "recipient_id", nullable = false, columnDefinition = "CHAR(36)")
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private IntroDirection direction;

    /** Optional context this introduction is about — a real Startup or Idea, never both, may be neither. */
    @Column(name = "startup_id", columnDefinition = "CHAR(36)")
    private String startupId;

    @Column(name = "idea_id", columnDefinition = "CHAR(36)")
    private String ideaId;

    @Column(nullable = false, length = 1000)
    private String message;

    @Convert(converter = IntroRequestStatusConverter.class)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private IntroRequestStatus status = IntroRequestStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
