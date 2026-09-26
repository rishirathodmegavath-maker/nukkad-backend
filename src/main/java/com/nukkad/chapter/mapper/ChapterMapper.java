package com.nukkad.chapter.mapper;

import com.nukkad.chapter.dto.ChapterDto;
import com.nukkad.chapter.entity.Chapter;
import org.springframework.stereotype.Component;

@Component
public class ChapterMapper {

    public ChapterDto toDto(Chapter chapter, long memberCount, long ideaCount, long startupCount, long opportunityCount,
                             long eventCount, long resourceCount, long discussionCount) {
        return new ChapterDto(
                chapter.getId(),
                chapter.getName(),
                chapter.getCity(),
                chapter.getCountry(),
                chapter.getDescription(),
                chapter.getCoverImageUrl(),
                chapter.getLogoUrl(),
                chapter.getPresidentUserId(),
                chapter.getFoundedAt(),
                chapter.getInstitution(),
                chapter.getType(),
                chapter.getFocusAreas(),
                memberCount,
                ideaCount,
                startupCount,
                opportunityCount,
                eventCount,
                resourceCount,
                discussionCount,
                chapter.getCreatedAt(),
                chapter.getUpdatedAt()
        );
    }
}
