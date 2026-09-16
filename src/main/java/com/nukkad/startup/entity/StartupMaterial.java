package com.nukkad.startup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

@Entity
@Table(name = "startup_materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartupMaterial {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "startup_id", nullable = false, columnDefinition = "CHAR(36)")
    private String startupId;

    @Convert(converter = StartupMaterialTypeConverter.class)
    @Column(name = "material_type", nullable = false, length = 20)
    private StartupMaterialType materialType;

    @Column(length = 200)
    private String title;

    /** Either an external link the founder supplied, or our own S3-hosted URL for an uploaded file. */
    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_by_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
