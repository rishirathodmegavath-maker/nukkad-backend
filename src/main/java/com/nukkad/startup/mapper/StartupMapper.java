package com.nukkad.startup.mapper;

import com.nukkad.startup.dto.StartupDto;
import com.nukkad.startup.dto.StartupRoleDto;
import com.nukkad.startup.dto.StartupTeamMemberDto;
import com.nukkad.startup.dto.StartupUpdateDto;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupRole;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.entity.StartupUpdate;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class StartupMapper {

    public StartupDto toDto(Startup startup) {
        return toDto(startup, false);
    }

    public StartupDto toDto(Startup startup, boolean isFollowing) {
        return new StartupDto(
                startup.getId(),
                startup.getName(),
                startup.getLogoUrl(),
                startup.getTagline(),
                startup.getSector(),
                startup.getProblem(),
                startup.getSolution(),
                startup.getStage().getLabel(),
                startup.getTraction(),
                startup.getIdeaId(),
                startup.getChapterId(),
                startup.isRaising(),
                new HashSet<>(startup.getNeeds()),
                isFollowing,
                startup.getCreatedAt(),
                startup.getUpdatedAt()
        );
    }

    public StartupTeamMemberDto toDto(StartupTeamMember member) {
        return new StartupTeamMemberDto(
                member.getId(),
                member.getStartupId(),
                member.getUserId(),
                member.getRole(),
                member.isFounder(),
                member.getStatus().name(),
                member.getRoleId(),
                member.getCreatedAt(),
                member.getReviewedAt()
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
