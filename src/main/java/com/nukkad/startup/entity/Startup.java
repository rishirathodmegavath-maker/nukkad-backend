package com.nukkad.startup.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "startups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Startup {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 300)
    private String tagline;

    @Column(length = 100)
    private String sector;

    @Column(columnDefinition = "TEXT")
    private String problem;

    @Column(columnDefinition = "TEXT")
    private String solution;

    @Convert(converter = StartupStageConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StartupStage stage = StartupStage.IDEA;

    @Column(columnDefinition = "TEXT")
    private String traction;

    @Column(name = "idea_id", columnDefinition = "CHAR(36)")
    private String ideaId;

    @Column(name = "chapter_id", columnDefinition = "CHAR(36)")
    private String chapterId;

    @Column(name = "is_raising", nullable = false)
    @Builder.Default
    private boolean isRaising = false;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "startup_needs", joinColumns = @JoinColumn(name = "startup_id"))
    @Column(name = "need", nullable = false)
    @Builder.Default
    private Set<String> needs = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
