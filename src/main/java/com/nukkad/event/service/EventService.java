package com.nukkad.event.service;

import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.event.dto.CreateEventRequest;
import com.nukkad.event.dto.EventDto;
import com.nukkad.event.dto.UpdateEventRequest;
import com.nukkad.event.entity.Event;
import com.nukkad.event.entity.EventAttendee;
import com.nukkad.event.mapper.EventMapper;
import com.nukkad.event.repository.EventAttendeeRepository;
import com.nukkad.event.repository.EventRepository;
import com.nukkad.event.repository.EventSpecifications;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventAttendeeRepository attendeeRepository;
    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EventMapper eventMapper;
    private final NotificationService notificationService;

    public EventService(EventRepository eventRepository,
                         EventAttendeeRepository attendeeRepository,
                         ChapterRepository chapterRepository,
                         UserRepository userRepository,
                         UserService userService,
                         EventMapper eventMapper,
                         NotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.attendeeRepository = attendeeRepository;
        this.chapterRepository = chapterRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.eventMapper = eventMapper;
        this.notificationService = notificationService;
    }

    public Event getEntityOrThrow(String id) {
        return eventRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<EventDto> listEvents(String chapterId, Boolean upcoming, String q, String organizerUserId, String viewerId, int page, int size) {
        Specification<Event> spec = EventSpecifications.combine(
                EventSpecifications.chapterId(chapterId),
                EventSpecifications.upcoming(upcoming),
                EventSpecifications.search(q),
                EventSpecifications.organizerUserId(organizerUserId)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "startAt"));
        return eventRepository.findAll(spec, pageable).map(event -> toDto(event, viewerId));
    }

    @Transactional(readOnly = true)
    public EventDto getEvent(String id, String viewerId) {
        return toDto(getEntityOrThrow(id), viewerId);
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAttendees(String eventId) {
        getEntityOrThrow(eventId);
        return attendeeRepository.findByEventIdOrderByRegisteredAtAsc(eventId).stream()
                .map(a -> userService.getUser(a.getUserId(), null))
                .toList();
    }

    @Transactional
    public EventDto createEvent(String userId, CreateEventRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BadRequestException("Event end time must be after the start time");
        }
        validateLocation(request.online(), request.location(), request.meetingUrl());

        String chapterId = null;
        if (request.chapterId() != null && !request.chapterId().isBlank()) {
            Chapter chapter = chapterRepository.findById(request.chapterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + request.chapterId()));
            requireChapterPresident(userId, chapter);
            chapterId = chapter.getId();
        }

        Event event = Event.builder()
                .title(request.title().trim())
                .description(request.description())
                .chapterId(chapterId)
                .organizerUserId(userId)
                .startAt(request.startAt())
                .endAt(request.endAt())
                .online(request.online())
                .location(request.location())
                .meetingUrl(request.meetingUrl())
                .coverImageUrl(request.coverImageUrl())
                .capacity(request.capacity())
                .build();
        event = eventRepository.saveAndFlush(event);

        return toDto(event, userId);
    }

    @Transactional
    public EventDto updateEvent(String userId, String eventId, UpdateEventRequest request) {
        Event event = getEntityOrThrow(eventId);
        requireEventManager(userId, event);

        Instant newStart = request.startAt() != null ? request.startAt() : event.getStartAt();
        Instant newEnd = request.endAt() != null ? request.endAt() : event.getEndAt();
        if (!newEnd.isAfter(newStart)) {
            throw new BadRequestException("Event end time must be after the start time");
        }
        boolean newOnline = request.online() != null ? request.online() : event.isOnline();
        String newLocation = request.location() != null ? request.location() : event.getLocation();
        String newMeetingUrl = request.meetingUrl() != null ? request.meetingUrl() : event.getMeetingUrl();
        validateLocation(newOnline, newLocation, newMeetingUrl);

        boolean logisticsChanged = !newStart.equals(event.getStartAt())
                || !newEnd.equals(event.getEndAt())
                || newOnline != event.isOnline()
                || !Objects.equals(newLocation, event.getLocation())
                || !Objects.equals(newMeetingUrl, event.getMeetingUrl());

        if (request.title() != null) event.setTitle(request.title());
        if (request.description() != null) event.setDescription(request.description());
        event.setStartAt(newStart);
        event.setEndAt(newEnd);
        event.setOnline(newOnline);
        event.setLocation(newLocation);
        event.setMeetingUrl(newMeetingUrl);
        if (request.coverImageUrl() != null) event.setCoverImageUrl(request.coverImageUrl());
        if (request.capacity() != null) event.setCapacity(request.capacity());

        event = eventRepository.saveAndFlush(event);

        if (logisticsChanged) {
            Event finalEvent = event;
            attendeeRepository.findByEventIdOrderByRegisteredAtAsc(eventId).forEach(a ->
                    notificationService.notify(a.getUserId(), NotificationType.event,
                            "Event updated", "Details for " + finalEvent.getTitle() + " have changed",
                            finalEvent.getId(), userId));
        }

        return toDto(event, userId);
    }

    @Transactional
    public void deleteEvent(String userId, String eventId) {
        Event event = getEntityOrThrow(eventId);
        requireEventManager(userId, event);

        List<EventAttendee> attendees = attendeeRepository.findByEventIdOrderByRegisteredAtAsc(eventId);
        String title = event.getTitle();
        eventRepository.delete(event);

        attendees.forEach(a -> notificationService.notify(a.getUserId(), NotificationType.event,
                "Event cancelled", "\"" + title + "\" has been cancelled by the organizer", null, userId));
    }

    @Transactional
    public EventDto rsvp(String userId, String eventId) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (attendeeRepository.existsByEventIdAndUserId(eventId, userId)) {
            throw new ConflictException("You're already registered for this event");
        }
        if (event.getCapacity() != null && attendeeRepository.countByEventId(eventId) >= event.getCapacity()) {
            throw new ConflictException("This event is full");
        }

        attendeeRepository.saveAndFlush(EventAttendee.builder().eventId(eventId).userId(userId).build());

        notificationService.notify(userId, NotificationType.event,
                "You're registered", "You're registered for " + event.getTitle(), event.getId(), null);
        if (!userId.equals(event.getOrganizerUserId())) {
            notificationService.notify(event.getOrganizerUserId(), NotificationType.event,
                    "New RSVP", "Someone registered for " + event.getTitle(), event.getId(), userId);
        }

        return toDto(event, userId);
    }

    @Transactional
    public EventDto cancelRsvp(String userId, String eventId) {
        Event event = getEntityOrThrow(eventId);
        EventAttendee attendee = attendeeRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BadRequestException("You are not registered for this event"));
        attendeeRepository.delete(attendee);
        return toDto(event, userId);
    }

    private void validateLocation(boolean online, String location, String meetingUrl) {
        if (online && (meetingUrl == null || meetingUrl.isBlank())) {
            throw new BadRequestException("An online event needs a meeting link");
        }
        if (!online && (location == null || location.isBlank())) {
            throw new BadRequestException("An in-person event needs a location");
        }
    }

    private void requireEventManager(String userId, Event event) {
        if (event.getChapterId() == null) {
            if (!userId.equals(event.getOrganizerUserId())) {
                throw new ForbiddenException("Only this event's organizer can manage it");
            }
            return;
        }
        Chapter chapter = chapterRepository.findById(event.getChapterId())
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + event.getChapterId()));
        requireChapterPresident(userId, chapter);
    }

    private void requireChapterPresident(String userId, Chapter chapter) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        boolean hasPresidentRole = user.getSecurityRoles().contains(SecurityRole.CHAPTER_PRESIDENT);
        boolean isPresidentOfThisChapter = userId.equals(chapter.getPresidentUserId());
        if (!hasPresidentRole || !isPresidentOfThisChapter) {
            throw new ForbiddenException("Only this chapter's president can manage its events");
        }
    }

    private boolean canManage(String userId, Event event) {
        if (userId == null) return false;
        if (event.getChapterId() == null) return userId.equals(event.getOrganizerUserId());
        return chapterRepository.findById(event.getChapterId())
                .map(chapter -> {
                    boolean hasPresidentRole = userRepository.findById(userId)
                            .map(u -> u.getSecurityRoles().contains(SecurityRole.CHAPTER_PRESIDENT))
                            .orElse(false);
                    return hasPresidentRole && userId.equals(chapter.getPresidentUserId());
                })
                .orElse(false);
    }

    private EventDto toDto(Event event, String viewerId) {
        String chapterName = event.getChapterId() == null ? null
                : chapterRepository.findById(event.getChapterId()).map(Chapter::getName).orElse(null);
        long attendeeCount = attendeeRepository.countByEventId(event.getId());
        boolean isAttending = viewerId != null && attendeeRepository.existsByEventIdAndUserId(event.getId(), viewerId);
        boolean manage = viewerId != null && canManage(viewerId, event);
        return eventMapper.toDto(event, chapterName, attendeeCount, isAttending, manage);
    }
}
