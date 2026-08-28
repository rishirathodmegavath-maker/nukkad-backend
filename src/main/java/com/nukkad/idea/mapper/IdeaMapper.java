package com.nukkad.idea.mapper;

import com.nukkad.idea.dto.IdeaDto;
import com.nukkad.idea.entity.ContributionArea;
import com.nukkad.idea.entity.Idea;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.stream.Collectors;

@Component
public class IdeaMapper {

    public IdeaDto toDto(Idea idea, int interestCount) {
        return new IdeaDto(
                idea.getId(),
                idea.getTitle(),
                idea.getProblem(),
                idea.getSolution(),
                idea.getTargetCustomer(),
                idea.getStage().getLabel(),
                idea.getCategory(),
                idea.getCreatorId(),
                idea.getChapterId(),
                idea.getStartupId(),
                new HashSet<>(idea.getTags()),
                idea.getHelpNeeded().stream().map(ContributionArea::getLabel).collect(Collectors.toSet()),
                new HashSet<>(idea.getTeamMemberIds()),
                interestCount,
                idea.getCreatedAt(),
                idea.getUpdatedAt()
        );
    }

}
