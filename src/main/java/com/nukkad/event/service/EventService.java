package com.nukkad.event.service;

import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.common.paging.PageRequests;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.common.validation.LinkSanitizer;
import com.nukkad.event.dto.CreateEventRequest;
import com.nukkad.event.dto.EventCoverImageDto;
import com.nukkad.event.dto.EventDto;
import com.nukkad.event.dto.EventStartupSummaryDto;
import com.nukkad.event.dto.StartupEventSummaryDto;
import com.nukkad.event.dto.UpdateEventRequest;
import com.nukkad.event.entity.Event;
import com.nukkad.event.entity.EventAttendee;
import com.nukkad.event.entity.EventStartup;
import com.nukkad.event.entity.EventStatus;
import com.nukkad.event.mapper.EventMapper;
import com.nukkad.event.repository.EventAttendeeRepository;
import com.nukkad.event.repository.EventIdCount;
import com.nukkad.event.repository.EventRepository;
import com.nukkad.event.repository.EventSpecifications;
import com.nukkad.event.repository.EventStartupRepository;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import com.nukkad.startup.service.StartupAccessPolicy;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EventService {

    /** Founder + Admin — who may tag a startup they manage onto an event. */
    private static final List<StartupTeamMember.TeamRole> MANAGER_ROLES =
            List.of(StartupTeamMember.TeamRole.FOUNDER, StartupTeamMember.TeamRole.ADMIN);

    private final EventRepository eventRepository;
    private final EventAttendeeRepository attendeeRepository;
    private final EventStartupRepository eventStartupRepository;
    private final ChapterRepository chapterRepository;
    private final StartupRepository startupRepository;
    private final StartupTeamMemberRepository startupTeamMemberRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EventMapper eventMapper;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final StartupAccessPolicy startupAccessPolicy;

    public EventService(EventRepository eventRepository,
                         EventAttendeeRepository attendeeRepository,
                         EventStartupRepository eventStartupRepository,
                         ChapterRepository chapterRepository,
                         StartupRepository startupRepository,
                         StartupTeamMemberRepository startupTeamMemberRepository,
                         UserRepository userRepository,
                         UserService userService,
                         EventMapper eventMapper,
                         NotificationService notificationService,
                         FileStorageService fileStorageService,
                         StartupAccessPolicy startupAccessPolicy) {
        this.eventRepository = eventRepository;
        this.attendeeRepository = attendeeRepository;
        this.eventStartupRepository = eventStartupRepository;
        this.chapterRepository = chapterRepository;
        this.startupRepository = startupRepository;
        this.startupTeamMemberRepository = startupTeamMemberRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.eventMapper = eventMapper;
        this.notificationService = notificationService;
        this.fileStorageService = fileStorageService;
        this.startupAccessPolicy = startupAccessPolicy;
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
        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "startAt"));
        Page<Event> results = eventRepository.findAll(spec, pageable);
        return results.map(batchEventDtoMapper(results.getContent(), viewerId));
    }

    @Transactional(readOnly = true)
    public EventDto getEvent(String id, String viewerId) {
        return toDto(getEntityOrThrow(id), viewerId);
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAttendees(String eventId, String viewerId) {
        getEntityOrThrow(eventId);
        return attendeeRepository.findByEventIdOrderByRegisteredAtAsc(eventId).stream()
                .map(a -> userService.getUser(a.getUserId(), viewerId))
                .toList();
    }

    /** Largest cover image accepted. Phone photos are usually 2-6 MB; anything bigger only slows the event page down. */
    static final long MAX_COVER_IMAGE_BYTES = 8L * 1024 * 1024;

    /**
     * Stores an image chosen as an event's cover and returns its public URL. The caller then sends that URL as
     * the event's {@code coverImageUrl} when creating or saving the event, so this works before the event exists.
     * Image type and emptiness are checked by {@link FileStorageService#storeImage}.
     */
    public EventCoverImageDto uploadCoverImage(MultipartFile file) {
        if (file != null && file.getSize() > MAX_COVER_IMAGE_BYTES) {
            throw new BadRequestException("Cover image is too large. The maximum size is 8 MB.");
        }
        return new EventCoverImageDto(fileStorageService.storeImage(file, "event-covers"));
    }

    @Transactional
    public EventDto createEvent(String userId, CreateEventRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BadRequestException("Event end time must be after the start time");
        }
        // One that is already under way is fine; one that has already finished can never be attended.
        if (request.endAt().isBefore(Instant.now())) {
            throw new BadRequestException("An event can't end in the past");
        }
        String meetingUrl = LinkSanitizer.normalizeHttpUrl(request.meetingUrl(), "Meeting link");
        String coverImageUrl = LinkSanitizer.normalizeHttpUrl(request.coverImageUrl(), "Cover image");
        validateLocation(request.online(), request.location(), meetingUrl);

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
                .meetingUrl(meetingUrl)
                .coverImageUrl(coverImageUrl)
                .capacity(request.capacity())
                .build();
        event = eventRepository.saveAndFlush(event);

        if (request.startupIds() != null && !request.startupIds().isEmpty()) {
            setEventStartups(userId, event.getId(), request.startupIds());
        }

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
        String newMeetingUrl = request.meetingUrl() != null
                ? LinkSanitizer.normalizeHttpUrl(request.meetingUrl(), "Meeting link") : event.getMeetingUrl();
        validateLocation(newOnline, newLocation, newMeetingUrl);
        if (request.capacity() != null) {
            long registered = attendeeRepository.countByEventId(eventId);
            if (request.capacity() < registered) {
                throw new BadRequestException("Capacity can't be lower than the " + registered + " people already registered");
            }
        }

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
        if (request.coverImageUrl() != null) event.setCoverImageUrl(LinkSanitizer.normalizeHttpUrl(request.coverImageUrl(), "Cover image"));
        if (request.capacity() != null) event.setCapacity(request.capacity());

        event = eventRepository.saveAndFlush(event);

        if (request.startupIds() != null) {
            setEventStartups(userId, eventId, request.startupIds());
        }

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
        if (EventStatus.of(event, Instant.now()) == EventStatus.ENDED) {
            throw new BadRequestException("This event has already ended");
        }
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
        return eventMapper.toDto(event, chapterName, attendeeCount, isAttending, manage, getEventStartups(event.getId(), viewerId, manage));
    }

    /**
     * The same per-viewer/per-event fields as {@link #toDto}, but for a whole page at once: a handful of
     * queries total (chapters, attendee counts, the viewer's own RSVPs, the viewer's own manager role, linked
     * startups, the startups the viewer manages) instead of the ~8 queries {@link #toDto} ran per row
     * (a chapter lookup for the name and another inside {@code canManage}, an attendee count, an RSVP check, a
     * president-role check, and {@code getEventStartups}'s own 2-3 queries — all repeated for every event).
     */
    private Function<Event, EventDto> batchEventDtoMapper(List<Event> events, String viewerId) {
        List<String> eventIds = events.stream().map(Event::getId).toList();
        if (eventIds.isEmpty()) {
            return event -> toDto(event, viewerId);
        }
        List<String> chapterIds = events.stream().map(Event::getChapterId).filter(Objects::nonNull).distinct().toList();
        Map<String, Chapter> chapterById = chapterIds.isEmpty() ? Map.of()
                : chapterRepository.findAllById(chapterIds).stream().collect(Collectors.toMap(Chapter::getId, c -> c));
        Map<String, Long> attendeeCounts = attendeeRepository.countGroupedByEventIdIn(eventIds).stream()
                .collect(Collectors.toMap(EventIdCount::getEventId, EventIdCount::getTotal));
        Set<String> viewerAttendingEventIds = viewerId == null ? Set.of()
                : attendeeRepository.findByEventIdInAndUserId(eventIds, viewerId).stream()
                        .map(EventAttendee::getEventId).collect(Collectors.toSet());
        boolean viewerHasPresidentRole = viewerId != null && userRepository.findById(viewerId)
                .map(u -> u.getSecurityRoles().contains(SecurityRole.CHAPTER_PRESIDENT)).orElse(false);

        Map<String, List<EventStartup>> eventStartupsByEventId = eventStartupRepository.findByEventIdIn(eventIds).stream()
                .collect(Collectors.groupingBy(EventStartup::getEventId));
        List<String> linkedStartupIds = eventStartupsByEventId.values().stream().flatMap(List::stream)
                .map(EventStartup::getStartupId).distinct().toList();
        Map<String, Startup> startupById = linkedStartupIds.isEmpty() ? Map.of()
                : startupRepository.findAllById(linkedStartupIds).stream().collect(Collectors.toMap(Startup::getId, s -> s));
        Set<String> startupsManagedByViewer = viewerId == null ? Set.of()
                : startupTeamMemberRepository.findByUserIdAndTeamRoleInAndStatus(viewerId, MANAGER_ROLES, StartupTeamMember.Status.ACTIVE)
                        .stream().map(StartupTeamMember::getStartupId).collect(Collectors.toSet());

        return event -> {
            Chapter chapter = event.getChapterId() == null ? null : chapterById.get(event.getChapterId());
            String chapterName = chapter == null ? null : chapter.getName();
            long attendeeCount = attendeeCounts.getOrDefault(event.getId(), 0L);
            boolean isAttending = viewerAttendingEventIds.contains(event.getId());
            boolean manage;
            if (viewerId == null) {
                manage = false;
            } else if (event.getChapterId() == null) {
                manage = viewerId.equals(event.getOrganizerUserId());
            } else {
                manage = chapter != null && viewerHasPresidentRole && viewerId.equals(chapter.getPresidentUserId());
            }
            boolean viewerRunsEvent = manage;
            List<EventStartupSummaryDto> startups = eventStartupsByEventId.getOrDefault(event.getId(), List.of()).stream()
                    .map(es -> startupById.get(es.getStartupId()))
                    .filter(Objects::nonNull)
                    .filter(s -> startupAccessPolicy.isReadableBy(s, viewerId))
                    .map(s -> new EventStartupSummaryDto(s.getId(), s.getName(), s.getLogoUrl(),
                            viewerRunsEvent || startupsManagedByViewer.contains(s.getId())))
                    .toList();
            return eventMapper.toDto(event, chapterName, attendeeCount, isAttending, manage, startups);
        };
    }

    /** Only the startups the viewer may see: a removed or rejected startup's name and logo must not leak through an event page. */
    private List<EventStartupSummaryDto> getEventStartups(String eventId, String viewerId, boolean viewerRunsEvent) {
        List<String> startupIds = eventStartupRepository.findByEventId(eventId).stream()
                .map(EventStartup::getStartupId).toList();
        if (startupIds.isEmpty()) return List.of();
        Map<String, Startup> byId = new LinkedHashMap<>();
        startupRepository.findAllById(startupIds).forEach(s -> byId.put(s.getId(), s));
        // The startups this viewer founds or administers: they may take those off the event even if they don't run it.
        Set<String> managedByViewer = viewerId == null || viewerRunsEvent ? Set.of()
                : startupTeamMemberRepository.findByUserIdAndTeamRoleInAndStatus(viewerId, MANAGER_ROLES, StartupTeamMember.Status.ACTIVE).stream()
                        .map(StartupTeamMember::getStartupId).collect(Collectors.toSet());
        return startupIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(s -> startupAccessPolicy.isReadableBy(s, viewerId))
                .map(s -> new EventStartupSummaryDto(s.getId(), s.getName(), s.getLogoUrl(), viewerRunsEvent || managedByViewer.contains(s.getId())))
                .toList();
    }

    /** Whether the person is an active founder or admin of the startup. */
    private boolean managesStartup(String userId, String startupId) {
        return userId != null && startupTeamMemberRepository
                .existsByStartupIdAndUserIdAndTeamRoleInAndStatus(startupId, userId, MANAGER_ROLES, StartupTeamMember.Status.ACTIVE);
    }

    /** A startup may be put on an event by someone who manages it, and only while it is live (not removed, not rejected). */
    private void requireCanLink(String userId, String startupId, Map<String, Startup> startups) {
        if (!managesStartup(userId, startupId)) {
            throw new ForbiddenException("You can only tag a startup you manage onto an event");
        }
        Startup startup = startups.get(startupId);
        if (startup == null || startup.isRemovedByAdmin() || startup.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new BadRequestException("This startup can't be added to an event");
        }
    }

    /**
     * Makes the event's startups match the list, changing only what differs: a startup already on the event keeps its
     * link (so nobody has to manage every startup the event already carries just to edit it), a new one must be managed
     * by the person adding it, and one that is left out is removed, unless it is one this person can't see.
     */
    private void setEventStartups(String userId, String eventId, List<String> requested) {
        List<String> wanted = requested.stream().distinct().toList();
        List<EventStartup> current = eventStartupRepository.findByEventId(eventId);
        Set<String> linked = current.stream().map(EventStartup::getStartupId).collect(Collectors.toSet());

        List<String> involved = new ArrayList<>(wanted);
        linked.stream().filter(id -> !wanted.contains(id)).forEach(involved::add);
        Map<String, Startup> startups = new HashMap<>();
        startupRepository.findAllById(involved).forEach(s -> startups.put(s.getId(), s));

        List<String> toAdd = wanted.stream().filter(id -> !linked.contains(id)).toList();
        for (String startupId : toAdd) requireCanLink(userId, startupId, startups);

        List<EventStartup> toRemove = current.stream()
                .filter(link -> !wanted.contains(link.getStartupId()))
                .filter(link -> {
                    Startup startup = startups.get(link.getStartupId());
                    return startup != null && startupAccessPolicy.isReadableBy(startup, userId);
                })
                .toList();

        if (!toRemove.isEmpty()) eventStartupRepository.deleteAll(toRemove);
        toAdd.forEach(startupId -> eventStartupRepository.save(EventStartup.builder().eventId(eventId).startupId(startupId).build()));
    }

    /** Puts a startup on an event. Needs both: the person runs the event, and manages the startup. Once only. */
    @Transactional
    public EventDto linkStartup(String userId, String eventId, String startupId) {
        Event event = getEntityOrThrow(eventId);
        requireEventManager(userId, event);
        Map<String, Startup> startups = new HashMap<>();
        startupRepository.findById(startupId).ifPresent(s -> startups.put(s.getId(), s));
        requireCanLink(userId, startupId, startups);
        if (eventStartupRepository.existsByEventIdAndStartupId(eventId, startupId)) {
            throw new ConflictException("This startup is already on this event");
        }
        eventStartupRepository.save(EventStartup.builder().eventId(eventId).startupId(startupId).build());
        return toDto(event, userId);
    }

    /** Takes a startup off an event. Either side may: whoever runs the event, or a founder/admin of the startup. */
    @Transactional
    public void unlinkStartup(String userId, String eventId, String startupId) {
        Event event = getEntityOrThrow(eventId);
        if (!canManage(userId, event) && !managesStartup(userId, startupId)) {
            throw new ForbiddenException("Only the event's organizer, or a founder or admin of the startup, can take it off the event");
        }
        EventStartup link = eventStartupRepository.findByEventIdAndStartupId(eventId, startupId)
                .orElseThrow(() -> new ResourceNotFoundException("This startup isn't on this event"));
        eventStartupRepository.delete(link);
    }

    @Transactional(readOnly = true)
    public List<StartupEventSummaryDto> getEventsForStartup(String startupId, String viewerId) {
        startupAccessPolicy.requireReadable(startupId, viewerId);
        List<String> eventIds = eventStartupRepository.findByStartupId(startupId).stream()
                .map(EventStartup::getEventId).toList();
        if (eventIds.isEmpty()) return List.of();
        List<Event> events = eventRepository.findAllById(eventIds);
        Map<String, String> chapterNames = new HashMap<>();
        chapterRepository.findAllById(events.stream().map(Event::getChapterId).filter(Objects::nonNull).distinct().toList())
                .forEach(c -> chapterNames.put(c.getId(), c.getName()));
        Instant now = Instant.now();
        return events.stream()
                .sorted((a, b) -> a.getStartAt().compareTo(b.getStartAt()))
                .map(e -> new StartupEventSummaryDto(e.getId(), e.getTitle(), e.getStartAt(), e.getEndAt(), e.isOnline(), e.getLocation(),
                        e.getCoverImageUrl(), chapterNames.get(e.getChapterId()), EventStatus.of(e, now)))
                .toList();
    }
}
