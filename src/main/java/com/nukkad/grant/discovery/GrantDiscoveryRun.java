package com.nukkad.grant.discovery;

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

/** One scheduled discovery attempt for one DiscoveryBatch (see GrantDiscoveryScheduler /
 *  GrantDiscoveryService#runNextBatch) -- lets the admin panel see what the automated pipeline has
 *  actually been doing, and lets DiscoveryBatchCatalog round-robin batches by least-recently-run. */
@Entity
@Table(name = "grant_discovery_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrantDiscoveryRun {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "batch_government", nullable = false, length = 100)
    private String batchGovernment;

    @Column(name = "batch_topic", nullable = false, length = 100)
    private String batchTopic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscoveryRunStatus status;

    @Column(name = "schemes_found", nullable = false)
    @Builder.Default
    private int schemesFound = 0;

    @Column(name = "schemes_created", nullable = false)
    @Builder.Default
    private int schemesCreated = 0;

    @Column(name = "schemes_updated", nullable = false)
    @Builder.Default
    private int schemesUpdated = 0;

    @Column(name = "schemes_rejected", nullable = false)
    @Builder.Default
    private int schemesRejected = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
