package com.nukkad.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/** Canonical: userAId < userBId (string-ordered), enforced by the service layer. */
@Entity
@Table(name = "connections", uniqueConstraints = @UniqueConstraint(name = "uq_connections_pair", columnNames = {"user_a_id", "user_b_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Connection {

    public enum Status { PENDING, ACCEPTED, DECLINED }

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "user_a_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userAId;

    @Column(name = "user_b_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userBId;

    @Column(name = "requested_by", nullable = false, columnDefinition = "CHAR(36)")
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACCEPTED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
