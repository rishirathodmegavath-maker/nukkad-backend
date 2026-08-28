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
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "startup_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartupRole {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "startup_id", nullable = false, columnDefinition = "CHAR(36)")
    private String startupId;

    @Column(nullable = false, length = 200)
    private String title;

    @Convert(converter = StartupRoleTypeConverter.class)
    @Column(nullable = false, length = 20)
    private StartupRoleType type;

    @Column(length = 200)
    private String location;

    @Column(nullable = false)
    @Builder.Default
    private boolean remote = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
