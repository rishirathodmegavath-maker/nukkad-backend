package com.nukkad.event.mapper;

import com.nukkad.event.dto.EventDto;
import com.nukkad.event.dto.EventStartupSummaryDto;
import com.nukkad.event.entity.Event;
import com.nukkad.event.entity.EventStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class EventMapper {

    public EventDto toDto(Event event, String chapterName, long attendeeCount, boolean isAttending, boolean canManage,
                           List<EventStartupSummaryDto> startups) {
        return new EventDto(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getChapterId(),
                chapterName,
                event.getOrganizerUserId(),
                event.getStartAt(),
                event.getEndAt(),
                EventStatus.of(event, Instant.now()),
                event.isOnline(),
                event.getLocation(),
                event.getMeetingUrl(),
                event.getCoverImageUrl(),
                event.getCapacity(),
                attendeeCount,
                isAttending,
                canManage,
                startups,
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
