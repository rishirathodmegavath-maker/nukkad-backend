package com.nukkad.opportunity.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "opportunities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Opportunity {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String title;

    @Convert(converter = OpportunityTypeConverter.class)
    @Column(nullable = false, length = 20)
    private OpportunityType type;

    @Column(name = "startup_id", columnDefinition = "CHAR(36)")
    private String startupId;

    @Column(name = "organization_name", nullable = false, length = 200)
    private String organizationName;

    @Column(length = 200)
    private String location;

    @Column(nullable = false)
    @Builder.Default
    private boolean remote = false;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String compensation;

    @Column(name = "posted_by_user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String postedByUserId;

    @Column(name = "chapter_id", columnDefinition = "CHAR(36)")
    private String chapterId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "opportunity_requirements", joinColumns = @JoinColumn(name = "opportunity_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "requirement", nullable = false)
    @Builder.Default
    private List<String> requirements = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
