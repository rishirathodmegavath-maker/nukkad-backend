package com.nukkad.event.mapper;

import com.nukkad.event.dto.EventDto;
import com.nukkad.event.entity.Event;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public EventDto toDto(Event event, String chapterName, long attendeeCount, boolean isAttending, boolean canManage) {
        return new EventDto(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getChapterId(),
                chapterName,
                event.getOrganizerUserId(),
                event.getStartAt(),
                event.getEndAt(),
                event.isOnline(),
                event.getLocation(),
                event.getMeetingUrl(),
                event.getCoverImageUrl(),
                event.getCapacity(),
                attendeeCount,
                isAttending,
                canManage,
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
