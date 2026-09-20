package com.nukkad.resource.entity;

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
@Table(name = "resources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Convert(converter = ResourceTypeConverter.class)
    @Column(nullable = false, length = 20)
    private ResourceType type;

    /** The shelf this sits on in the library; null for older resources that were never filed. */
    @Convert(converter = ResourceCategoryConverter.class)
    @Column(length = 30)
    private ResourceCategory category;

    /** Where the content comes from, as shown to members ("Y Combinator"). */
    @Column(length = 120)
    private String provider;

    /** A hosted image (uploaded by an admin) shown on cards; null means the client derives or draws one. */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** Shown on the library's front page shelf. */
    @Column(nullable = false)
    @Builder.Default
    private boolean featured = false;

    /** Either an external link the uploader supplied, or our own /uploads/... URL for an uploaded file. */
    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "uploader_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String uploaderUserId;

    /** Null means a platform-wide resource (no owning chapter). */
    @Column(name = "chapter_id", columnDefinition = "CHAR(36)")
    private String chapterId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "resource_tags", joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "tag", nullable = false)
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
