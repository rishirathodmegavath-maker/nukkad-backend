package com.nukkad.startup.service;

import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.entity.StartupVisibility;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import org.springframework.stereotype.Component;

/**
 * The one place that decides whether a viewer may READ a startup, and therefore anything hanging off it (its team,
 * materials, updates, roles, fundraise, tagged events, introduction requests). Every read path goes through here so the
 * rules cannot drift apart between endpoints:
 * <ul>
 *   <li>a startup an admin removed is invisible to everyone (admins use the admin endpoints);</li>
 *   <li>a member-only startup is invisible to an anonymous caller;</li>
 *   <li>a startup that is not APPROVED (a legacy rejection) is visible only to its own founders/admins.</li>
 * </ul>
 * Every "no" is reported as a plain 404, so the response never reveals that a hidden startup exists.
 */
@Component
public class StartupAccessPolicy {

    private final StartupRepository startupRepository;
    private final StartupTeamMemberRepository teamMemberRepository;

    public StartupAccessPolicy(StartupRepository startupRepository, StartupTeamMemberRepository teamMemberRepository) {
        this.startupRepository = startupRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    /** Whether {@code viewerId} (null for an anonymous caller) may read this startup. */
    public boolean isReadableBy(Startup startup, String viewerId) {
        if (startup.isRemovedByAdmin()) return false;
        if (viewerId == null && startup.getVisibility() == StartupVisibility.NUKKAD_MEMBERS) return false;
        if (startup.getModerationStatus() != ModerationStatus.APPROVED) return canManage(startup.getId(), viewerId);
        return true;
    }

    /** The startup, or a 404 when it does not exist or the viewer may not read it. */
    public Startup requireReadable(String startupId, String viewerId) {
        Startup startup = startupRepository.findById(startupId)
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found: " + startupId));
        if (!isReadableBy(startup, viewerId)) {
            throw new ResourceNotFoundException("Startup not found: " + startupId);
        }
        return startup;
    }

    /** An active founder or admin of the startup. */
    public boolean canManage(String startupId, String userId) {
        if (userId == null) return false;
        return teamMemberRepository.findByStartupIdAndUserId(startupId, userId)
                .map(m -> m.canManage() && m.getStatus() == StartupTeamMember.Status.ACTIVE)
                .orElse(false);
    }
}
