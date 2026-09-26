package com.nukkad.chapter.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "chapters", uniqueConstraints = @UniqueConstraint(name = "uq_chapters_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chapter {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "president_user_id", columnDefinition = "CHAR(36)")
    private String presidentUserId;

    @Column(length = 150)
    private String institution;

    /** Free-text classification shown as a badge next to the chapter's name (e.g. "University
     *  Chapter", "City Chapter") — a small, president-editable label, not a fixed enum. */
    @Column(length = 50)
    private String type;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "chapter_focus_areas", joinColumns = @JoinColumn(name = "chapter_id"))
    @Column(name = "focus_area", nullable = false)
    @Builder.Default
    private Set<String> focusAreas = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
