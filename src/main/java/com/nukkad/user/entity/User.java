package com.nukkad.user.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
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
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(length = 200)
    private String headline;

    @Column(length = 150)
    private String role;

    @Column(name = "college_or_company", length = 200)
    private String collegeOrCompany;

    @Column(length = 200)
    private String location;

    @Column(name = "experience_years", nullable = false)
    @Builder.Default
    private int experienceYears = 0;

    @Column(columnDefinition = "TEXT")
    private String goals;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Convert(converter = AvailabilityConverter.class)
    @Column(length = 20)
    private Availability availability;

    @Column(name = "chapter_id", columnDefinition = "CHAR(36)")
    private String chapterId;

    @Column(name = "connections_count", nullable = false)
    @Builder.Default
    private int connectionsCount = 0;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_security_roles", joinColumns = @jakarta.persistence.JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private Set<SecurityRole> securityRoles = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_skills", joinColumns = @jakarta.persistence.JoinColumn(name = "user_id"))
    @Column(name = "skill", nullable = false)
    @Builder.Default
    private Set<String> skills = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_looking_for", joinColumns = @jakarta.persistence.JoinColumn(name = "user_id"))
    @Convert(converter = LookingForConverter.class)
    @Column(name = "looking_for", nullable = false)
    @Builder.Default
    private Set<LookingFor> lookingFor = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_open_to", joinColumns = @jakarta.persistence.JoinColumn(name = "user_id"))
    @Convert(converter = OpenToConverter.class)
    @Column(name = "open_to", nullable = false)
    @Builder.Default
    private Set<OpenTo> openTo = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_social_links", joinColumns = @jakarta.persistence.JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "platform")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "url", nullable = false, length = 300)
    @Builder.Default
    private java.util.Map<SocialPlatform, String> socialLinks = new java.util.HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
