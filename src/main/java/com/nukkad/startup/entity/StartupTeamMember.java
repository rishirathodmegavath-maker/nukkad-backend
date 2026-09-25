package com.nukkad.startup.entity;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "startup_team_members", uniqueConstraints = @UniqueConstraint(name = "uq_startup_team_user", columnNames = {"startup_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartupTeamMember {

    /**
     * ACTIVE is on the team. PENDING is someone who asked to join, waiting for a manager. INVITED is someone a manager
     * asked to join, waiting for them: it confers nothing until they accept, so nobody can be put on a team (and gain
     * the messaging access teammates have) without agreeing to it. REJECTED is a declined join request.
     */
    public enum Status { ACTIVE, PENDING, REJECTED, INVITED }

    public enum TeamRole { FOUNDER, ADMIN, MEMBER }

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "startup_id", nullable = false, columnDefinition = "CHAR(36)")
    private String startupId;

    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userId;

    @Column(length = 150)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_role", nullable = false, length = 10)
    @Builder.Default
    private TeamRole teamRole = TeamRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "role_id", columnDefinition = "CHAR(36)")
    private String roleId;

    @Column(length = 1000)
    private String message;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isFounder() {
        return teamRole == TeamRole.FOUNDER;
    }

    public boolean isAdmin() {
        return teamRole == TeamRole.ADMIN;
    }

    /** Founder or Admin — the tier that unlocks edit-startup/manage-team/post-jobs/edit-fundraising. */
    public boolean canManage() {
        return teamRole == TeamRole.FOUNDER || teamRole == TeamRole.ADMIN;
    }
}
