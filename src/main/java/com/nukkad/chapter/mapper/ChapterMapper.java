package com.nukkad.chapter.mapper;

import com.nukkad.chapter.dto.ChapterDto;
import com.nukkad.chapter.entity.Chapter;
import org.springframework.stereotype.Component;

import java.util.HashSet;

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
                // Copied, not passed through: focusAreas is a lazy @ElementCollection, and this DTO
                // is still serialized to JSON well after the transaction (and its Hibernate session)
                // has closed. Passing the live PersistentSet reference through crashes every list/get
                // with LazyInitializationException the moment Jackson touches it; a plain HashSet
                // forces the load now, while the session is still open, and is safe to hold onto.
                new HashSet<>(chapter.getFocusAreas()),
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
