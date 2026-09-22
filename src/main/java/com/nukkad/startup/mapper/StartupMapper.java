package com.nukkad.startup.mapper;

import com.nukkad.startup.dto.StartupDto;
import com.nukkad.startup.dto.StartupMaterialDto;
import com.nukkad.startup.dto.StartupRoleDto;
import com.nukkad.startup.dto.StartupTeamMemberDto;
import com.nukkad.startup.dto.StartupUpdateDto;
import com.nukkad.user.dto.UserDto;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupMaterial;
import com.nukkad.startup.entity.StartupRole;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.entity.StartupUpdate;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class StartupMapper {

    public StartupDto toDto(Startup startup) {
        return toDto(startup, false, false, true, 0);
    }

    /**
     * @param canManage          true for an active founder — drives edit/delete/manage-material affordances.
     * @param followerCount      how many members follow the startup: the real number, counted by the caller.
     *                           Only surfaced to a viewer who can manage the startup (its founders/admins)
     *                           — everyone else gets 0, same idea as {@code canViewFundraising} below. Who
     *                           follows a startup is a signal for its own team to read, not a public metric;
     *                           following itself still works for anyone, this only hides the count.
     * @param canViewFundraising true if this viewer may see real fundraising data: either the startup's
     *                           fundraising visibility is on, or the viewer is a founder/team member of
     *                           their own startup. When false, {@code isRaising} is also suppressed since
     *                           that status alone reveals the startup is fundraising.
     */
    public StartupDto toDto(Startup startup, boolean isFollowing, boolean canManage, boolean canViewFundraising, long followerCount) {
        return new StartupDto(
                startup.getId(),
                startup.getName(),
                startup.getLogoUrl(),
                startup.getLocation(),
                startup.getWebsite(),
                startup.getTagline(),
                startup.getSector(),
                startup.getProblem(),
                startup.getSolution(),
                startup.getTargetCustomer(),
                startup.getBusinessModel(),
                startup.getWhatBuilding(),
                startup.getStage().getLabel(),
                startup.getTraction(),
                startup.getRevenue(),
                startup.getCustomers(),
                startup.getUsers(),
                startup.getGrowth(),
                startup.getOtherTraction(),
                startup.getKeywords(),
                startup.getVisibility().getLabel(),
                startup.isFundraisingVisible(),
                startup.getIdeaId(),
                startup.getChapterId(),
                canViewFundraising && startup.isRaising(),
                new HashSet<>(startup.getNeeds()),
                isFollowing,
                canManage ? followerCount : 0,
                canManage,
                profileCompletionPercent(startup),
                startup.isRemovedByAdmin(),
                startup.getRemovalReason(),
                startup.getModerationStatus().name(),
                startup.getRejectionReason(),
                startup.getCreatedAt(),
                startup.getUpdatedAt()
        );
    }

    /** Percentage of the optional rich-profile fields that have actually been filled in — never
     *  a fabricated number, always derived from what's really persisted. */
    public int profileCompletionPercent(Startup startup) {
        String[] fields = {
                startup.getLogoUrl(), startup.getLocation(), startup.getWebsite(), startup.getTagline(),
                startup.getSector(), startup.getProblem(), startup.getSolution(), startup.getTargetCustomer(),
                startup.getBusinessModel(), startup.getWhatBuilding(),
        };
        int filled = 0;
        int total = fields.length + 2; // + traction-ish info + needs
        for (String field : fields) {
            if (field != null && !field.isBlank()) filled++;
        }
        boolean hasTraction = hasText(startup.getRevenue()) || hasText(startup.getCustomers())
                || hasText(startup.getUsers()) || hasText(startup.getGrowth()) || hasText(startup.getOtherTraction());
        if (hasTraction) filled++;
        if (!startup.getNeeds().isEmpty()) filled++;
        return Math.round(100f * filled / total);
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public StartupMaterialDto toDto(StartupMaterial material, boolean canManage) {
        return new StartupMaterialDto(
                material.getId(),
                material.getStartupId(),
                material.getMaterialType().getLabel(),
                material.getTitle(),
                material.getUrl(),
                material.getOriginalFileName(),
                material.getContentType(),
                material.getSortOrder(),
                canManage,
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }

    public StartupTeamMemberDto toDto(StartupTeamMember member) {
        return toDto(member, null);
    }

    public StartupTeamMemberDto toDto(StartupTeamMember member, UserDto user) {
        return new StartupTeamMemberDto(
                member.getId(),
                member.getStartupId(),
                member.getUserId(),
                member.getRole(),
                member.getTeamRole().name(),
                member.isFounder(),
                member.isAdmin(),
                member.canManage(),
                member.getStatus().name(),
                member.getRoleId(),
                member.getCreatedAt(),
                member.getReviewedAt(),
                user
        );
    }

    public StartupUpdateDto toDto(StartupUpdate update) {
        return new StartupUpdateDto(update.getId(), update.getStartupId(), update.getContent(), update.getCreatedAt());
    }

    public StartupRoleDto toDto(StartupRole role) {
        return new StartupRoleDto(
                role.getId(),
                role.getStartupId(),
                role.getTitle(),
                role.getType().getLabel(),
                role.getLocation(),
                role.isRemote(),
                role.getCreatedAt()
        );
    }
}
