package com.nukkad.event.service;

import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.event.dto.CreateEventRequest;
import com.nukkad.event.dto.EventDto;
import com.nukkad.event.dto.UpdateEventRequest;
import com.nukkad.event.entity.Event;
import com.nukkad.event.entity.EventAttendee;
import com.nukkad.event.mapper.EventMapper;
import com.nukkad.event.repository.EventAttendeeRepository;
import com.nukkad.event.repository.EventRepository;
import com.nukkad.event.repository.EventStartupRepository;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Covers Events authorization (chapter events are chapter-president-scoped; events created
 *  without a chapter are personal events managed solely by their organizer), RSVP/capacity,
 *  and the date/location validation rules. */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private EventAttendeeRepository attendeeRepository;
    @Mock private EventStartupRepository eventStartupRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private StartupRepository startupRepository;
    @Mock private StartupTeamMemberRepository startupTeamMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private NotificationService notificationService;
    @Mock private FileStorageService fileStorageService;

    private final EventMapper eventMapper = new EventMapper();

    private EventService service() {
        return new EventService(eventRepository, attendeeRepository, eventStartupRepository, chapterRepository,
                startupRepository, startupTeamMemberRepository, userRepository, userService, eventMapper, notificationService, fileStorageService,
                new com.nukkad.startup.service.StartupAccessPolicy(startupRepository, startupTeamMemberRepository));
    }

    private Chapter chapter(String id, String presidentUserId) {
        return Chapter.builder().id(id).name("Nukkad Bengaluru").presidentUserId(presidentUserId).build();
    }

    private User user(String id, SecurityRole... roles) {
        return User.builder().id(id).name("User " + id).securityRoles(new HashSet<>(Set.of(roles))).build();
    }

    private Event event(String id, String chapterId, String organizerId, Integer capacity) {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        return Event.builder().id(id).title("Demo night").chapterId(chapterId).organizerUserId(organizerId)
                .startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).online(false).location("HSR Layout")
                .capacity(capacity).build();
    }

    private CreateEventRequest validRequest(String chapterId) {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        return new CreateEventRequest("Demo night", "Come build", chapterId, start, start.plus(2, ChronoUnit.HOURS),
                false, "HSR Layout", null, null, null, null);
    }

    // ---- Listing a page of events batches the per-viewer/per-row lookups instead of running them once per row ----

    @Test
    void listingEventsFetchesChapterAttendeeAndStartupDataInFixedQueriesRegardlessOfPageSize() {
        Event e1 = event("ev1", null, "owner1", 10);
        Event e2 = event("ev2", null, "owner2", 10);
        when(eventRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(e1, e2)));
        when(attendeeRepository.countGroupedByEventIdIn(any())).thenReturn(java.util.List.of());
        when(attendeeRepository.findByEventIdInAndUserId(any(), org.mockito.ArgumentMatchers.eq("viewer1"))).thenReturn(java.util.List.of());
        when(eventStartupRepository.findByEventIdIn(any())).thenReturn(java.util.List.of());

        var page = service().listEvents(null, null, null, null, "viewer1", 0, 20);

        assertThat(page.getContent()).hasSize(2);
        // One call each for the whole page, not one per event — the actual N+1 fix.
        org.mockito.Mockito.verify(attendeeRepository, org.mockito.Mockito.times(1)).countGroupedByEventIdIn(any());
        org.mockito.Mockito.verify(attendeeRepository, org.mockito.Mockito.times(1))
                .findByEventIdInAndUserId(any(), org.mockito.ArgumentMatchers.eq("viewer1"));
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.times(1)).findByEventIdIn(any());
        org.mockito.Mockito.verify(attendeeRepository, org.mockito.Mockito.never()).countByEventId(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(attendeeRepository, org.mockito.Mockito.never())
                .existsByEventIdAndUserId(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).findByEventId(org.mockito.ArgumentMatchers.anyString());
    }

    // ---- create: authorization ----

    @Test
    void nonPresidentCannotCreateChapterEvent() {
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(chapter("c1", "president1")));
        when(userRepository.findById("regularUser")).thenReturn(Optional.of(user("regularUser", SecurityRole.USER)));

        assertThatThrownBy(() -> service().createEvent("regularUser", validRequest("c1")))
                .isInstanceOf(ForbiddenException.class);

        verify0Saves();
    }

    @Test
    void presidentOfAnotherChapterCannotCreateEventForThisChapter() {
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(chapter("c1", "president1")));
        // president2 genuinely holds the CHAPTER_PRESIDENT role, but of a different chapter.
        when(userRepository.findById("president2")).thenReturn(Optional.of(user("president2", SecurityRole.CHAPTER_PRESIDENT)));

        assertThatThrownBy(() -> service().createEvent("president2", validRequest("c1")))
                .isInstanceOf(ForbiddenException.class);

        verify0Saves();
    }

    @Test
    void chapterPresidentCanCreateEventForOwnChapter() {
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(chapter("c1", "president1")));
        when(userRepository.findById("president1")).thenReturn(Optional.of(user("president1", SecurityRole.CHAPTER_PRESIDENT)));
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId(any())).thenReturn(0L);

        EventDto dto = service().createEvent("president1", validRequest("c1"));

        assertThat(dto.organizerUserId()).isEqualTo("president1");
        assertThat(dto.chapterId()).isEqualTo("c1");
    }

    @Test
    void anyUserCanCreateAPersonalEventWithoutAChapter() {
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId(any())).thenReturn(0L);

        EventDto dto = service().createEvent("regularUser", validRequest(null));

        assertThat(dto.organizerUserId()).isEqualTo("regularUser");
        assertThat(dto.chapterId()).isNull();
        assertThat(dto.canManage()).isTrue();
    }

    @Test
    void invalidDateRangeIsRejected() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateEventRequest backwards = new CreateEventRequest("Bad event", null, null, start, start.minus(1, ChronoUnit.HOURS),
                false, "Somewhere", null, null, null, null);

        assertThatThrownBy(() -> service().createEvent("president1", backwards))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void onlineEventWithoutMeetingUrlIsRejected() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateEventRequest noLink = new CreateEventRequest("Webinar", null, null, start, start.plus(1, ChronoUnit.HOURS),
                true, null, null, null, null, null);

        assertThatThrownBy(() -> service().createEvent("president1", noLink))
                .isInstanceOf(BadRequestException.class);
    }

    // ---- update/delete: authorization ----

    @Test
    void presidentOfChapterACannotEditChapterBsEvent() {
        Event chapterBEvent = event("e1", "chapterB", "presidentB", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(chapterBEvent));
        when(chapterRepository.findById("chapterB")).thenReturn(Optional.of(chapter("chapterB", "presidentB")));
        when(userRepository.findById("presidentA")).thenReturn(Optional.of(user("presidentA", SecurityRole.CHAPTER_PRESIDENT)));

        assertThatThrownBy(() -> service().updateEvent("presidentA",
                "e1", new UpdateEventRequest("New title", null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void regularMemberCannotEditOrDeleteEvent() {
        Event chapterEvent = event("e1", "c1", "president1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(chapterEvent));
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(chapter("c1", "president1")));
        when(userRepository.findById("member1")).thenReturn(Optional.of(user("member1", SecurityRole.USER)));

        assertThatThrownBy(() -> service().updateEvent("member1",
                "e1", new UpdateEventRequest("New title", null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service().deleteEvent("member1", "e1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void chapterPresidentCanEditOwnChapterEvent() {
        Event chapterEvent = event("e1", "c1", "president1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(chapterEvent));
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(chapter("c1", "president1")));
        when(userRepository.findById("president1")).thenReturn(Optional.of(user("president1", SecurityRole.CHAPTER_PRESIDENT)));
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId("e1")).thenReturn(0L);

        EventDto dto = service().updateEvent("president1", "e1",
                new UpdateEventRequest("Updated title", null, null, null, null, null, null, null, null, null));

        assertThat(dto.title()).isEqualTo("Updated title");
    }

    @Test
    void organizerCanEditTheirOwnPersonalEvent() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId("e1")).thenReturn(0L);

        EventDto dto = service().updateEvent("organizer1", "e1",
                new UpdateEventRequest("Updated title", null, null, null, null, null, null, null, null, null));

        assertThat(dto.title()).isEqualTo("Updated title");
    }

    @Test
    void nonOrganizerCannotEditOrDeleteAnotherUsersPersonalEvent() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));

        assertThatThrownBy(() -> service().updateEvent("someoneElse",
                "e1", new UpdateEventRequest("Hijack", null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service().deleteEvent("someoneElse", "e1"))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- RSVP ----

    @Test
    void duplicateRsvpIsPrevented() {
        Event chapterEvent = event("e1", "c1", "president1", null);
        when(eventRepository.findByIdForUpdate("e1")).thenReturn(Optional.of(chapterEvent));
        when(attendeeRepository.existsByEventIdAndUserId("e1", "member1")).thenReturn(true);

        assertThatThrownBy(() -> service().rsvp("member1", "e1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rsvpAfterCapacityReachedIsRejected() {
        Event fullEvent = event("e1", "c1", "president1", 2);
        when(eventRepository.findByIdForUpdate("e1")).thenReturn(Optional.of(fullEvent));
        when(attendeeRepository.existsByEventIdAndUserId("e1", "member1")).thenReturn(false);
        when(attendeeRepository.countByEventId("e1")).thenReturn(2L);

        assertThatThrownBy(() -> service().rsvp("member1", "e1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rsvpUsesAuthenticatedUserIdAndCreatesRealAttendeeRecord() {
        Event openEvent = event("e1", "c1", "president1", null);
        when(eventRepository.findByIdForUpdate("e1")).thenReturn(Optional.of(openEvent));
        when(attendeeRepository.existsByEventIdAndUserId("e1", "member1")).thenReturn(false);
        when(attendeeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().rsvp("member1", "e1");

        org.mockito.ArgumentCaptor<EventAttendee> captor = org.mockito.ArgumentCaptor.forClass(EventAttendee.class);
        org.mockito.Mockito.verify(attendeeRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("member1");
        assertThat(captor.getValue().getEventId()).isEqualTo("e1");
    }

    @Test
    void cancellingRsvpRemovesTheAttendeeRecordAndAllowsRsvpingAgain() {
        Event openEvent = event("e1", "c1", "president1", null);
        EventAttendee existing = EventAttendee.builder().id("a1").eventId("e1").userId("member1").build();
        when(eventRepository.findById("e1")).thenReturn(Optional.of(openEvent));
        when(attendeeRepository.findByEventIdAndUserId("e1", "member1")).thenReturn(Optional.of(existing));

        service().cancelRsvp("member1", "e1");

        org.mockito.Mockito.verify(attendeeRepository).delete(existing);
    }

    @Test
    void cancellingWithoutAnExistingRsvpIsRejected() {
        Event openEvent = event("e1", "c1", "president1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(openEvent));
        when(attendeeRepository.findByEventIdAndUserId("e1", "member1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().cancelRsvp("member1", "e1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deletedEventCannotBeRsvpedTo() {
        when(eventRepository.findByIdForUpdate("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().rsvp("member1", "gone"))
                .isInstanceOf(com.nukkad.common.exception.ResourceNotFoundException.class);
    }

    private void verify0Saves() {
        org.mockito.Mockito.verify(eventRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    // ---- attendees: privacy ----

    @Test
    void listingAttendeesPassesTheRealViewerIdThroughToUserServiceRatherThanBypassingPrivacyWithNull() {
        // Regression test: this endpoint used to hardcode a null viewer id, which skips
        // UserService's whole profile-visibility check and always returns the unrestricted DTO —
        // any authenticated caller could read a CONNECTIONS-restricted attendee's full profile
        // (including email, before that was separately fixed) just by looking up an event they
        // can see. The fix threads the real authenticated viewer id through instead.
        Event openEvent = event("e1", "c1", "president1", null);
        EventAttendee attendee = EventAttendee.builder().id("a1").eventId("e1").userId("attendee1").build();
        when(eventRepository.findById("e1")).thenReturn(Optional.of(openEvent));
        when(attendeeRepository.findByEventIdOrderByRegisteredAtAsc("e1")).thenReturn(java.util.List.of(attendee));

        service().getAttendees("e1", "viewer1");

        org.mockito.Mockito.verify(userService).getUser("attendee1", "viewer1");
        org.mockito.Mockito.verify(userService, org.mockito.Mockito.never()).getUser("attendee1", null);
    }

    // ---- Startup <-> Event association ----

    @Test
    void creatingAnEventTagsAStartupTheOrganizerManages() {
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId(any())).thenReturn(0L);
        when(startupTeamMemberRepository.existsByStartupIdAndUserIdAndTeamRoleInAndStatus(
                "s1", "founder1", java.util.List.of(com.nukkad.startup.entity.StartupTeamMember.TeamRole.FOUNDER,
                        com.nukkad.startup.entity.StartupTeamMember.TeamRole.ADMIN),
                com.nukkad.startup.entity.StartupTeamMember.Status.ACTIVE)).thenReturn(true);
        when(eventStartupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        com.nukkad.startup.entity.Startup startup = com.nukkad.startup.entity.Startup.builder().id("s1").name("Ledgerly").build();
        when(startupRepository.findAllById(java.util.List.of("s1"))).thenReturn(java.util.List.of(startup));
        when(eventStartupRepository.findByEventId(any())).thenReturn(java.util.List.of(),
                java.util.List.of(com.nukkad.event.entity.EventStartup.builder().eventId("e1").startupId("s1").build()));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateEventRequest request = new CreateEventRequest("Demo night", "Come build", null, start,
                start.plus(2, ChronoUnit.HOURS), false, "HSR Layout", null, null, null, java.util.List.of("s1"));

        EventDto dto = service().createEvent("founder1", request);

        assertThat(dto.startups()).extracting("id").containsExactly("s1");
        org.mockito.Mockito.verify(eventStartupRepository).save(any());
    }

    @Test
    void cannotTagAStartupYouDoNotManage() {
        when(startupTeamMemberRepository.existsByStartupIdAndUserIdAndTeamRoleInAndStatus(
                "someoneElsesStartup", "user1", java.util.List.of(com.nukkad.startup.entity.StartupTeamMember.TeamRole.FOUNDER,
                        com.nukkad.startup.entity.StartupTeamMember.TeamRole.ADMIN),
                com.nukkad.startup.entity.StartupTeamMember.Status.ACTIVE)).thenReturn(false);
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateEventRequest request = new CreateEventRequest("Demo night", null, null, start,
                start.plus(2, ChronoUnit.HOURS), false, "HSR Layout", null, null, null, java.util.List.of("someoneElsesStartup"));

        assertThatThrownBy(() -> service().createEvent("user1", request)).isInstanceOf(ForbiddenException.class);
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).save(any());
    }

    private com.nukkad.startup.entity.Startup startupNamed(String id) {
        return com.nukkad.startup.entity.Startup.builder().id(id).name("Startup " + id).build();
    }

    private void userManagesStartup(String userId, String startupId, boolean manages) {
        when(startupTeamMemberRepository.existsByStartupIdAndUserIdAndTeamRoleInAndStatus(
                org.mockito.ArgumentMatchers.eq(startupId), org.mockito.ArgumentMatchers.eq(userId), any(), any())).thenReturn(manages);
    }

    private com.nukkad.event.entity.EventStartup link(String eventId, String startupId) {
        return com.nukkad.event.entity.EventStartup.builder().id("l-" + startupId).eventId(eventId).startupId(startupId).build();
    }

    @Test
    void updatingAnEventAddsTheNewStartupAndRemovesTheOneLeftOutWithoutTouchingTheRest() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId("e1")).thenReturn(0L);
        userManagesStartup("organizer1", "s2", true);
        when(eventStartupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventStartupRepository.findByEventId("e1")).thenReturn(java.util.List.of(link("e1", "s1"), link("e1", "s3")));
        when(startupRepository.findAllById(any())).thenReturn(java.util.List.of(startupNamed("s1"), startupNamed("s2"), startupNamed("s3")));

        // s3 stays, s2 is new, s1 is left out
        service().updateEvent("organizer1", "e1",
                new UpdateEventRequest(null, null, null, null, null, null, null, null, null, java.util.List.of("s3", "s2")));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Iterable<com.nukkad.event.entity.EventStartup>> removed = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        org.mockito.Mockito.verify(eventStartupRepository).deleteAll(removed.capture());
        assertThat(removed.getValue()).extracting("startupId").containsExactly("s1");
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.times(1)).save(any());
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).deleteByEventId(any());
    }

    @Test
    void editingAnEventNeverAsksYouToManageStartupsThatAreAlreadyOnIt() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId("e1")).thenReturn(0L);
        // the organizer manages nothing, but someone else's startup is already on the event
        when(eventStartupRepository.findByEventId("e1")).thenReturn(java.util.List.of(link("e1", "theirs")));
        when(startupRepository.findAllById(any())).thenReturn(java.util.List.of(startupNamed("theirs")));

        service().updateEvent("organizer1", "e1",
                new UpdateEventRequest("New title", null, null, null, null, null, null, null, null, java.util.List.of("theirs")));

        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).deleteAll(any());
    }

    @Test
    void aStartupTheEditorCannotSeeIsNotDroppedWhenTheyLeaveItOutOfTheList() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId("e1")).thenReturn(0L);
        com.nukkad.startup.entity.Startup removed = startupNamed("gone");
        removed.setRemovedByAdmin(true);
        when(eventStartupRepository.findByEventId("e1")).thenReturn(java.util.List.of(link("e1", "gone")));
        when(startupRepository.findAllById(any())).thenReturn(java.util.List.of(removed));

        service().updateEvent("organizer1", "e1",
                new UpdateEventRequest(null, null, null, null, null, null, null, null, null, java.util.List.of()));

        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).deleteAll(any());
    }

    @Test
    void aRemovedOrRejectedStartupCannotBeAddedToAnEvent() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        userManagesStartup("organizer1", "removed", true);
        userManagesStartup("organizer1", "rejected", true);
        com.nukkad.startup.entity.Startup removed = startupNamed("removed");
        removed.setRemovedByAdmin(true);
        com.nukkad.startup.entity.Startup rejected = startupNamed("rejected");
        rejected.setModerationStatus(com.nukkad.common.moderation.ModerationStatus.REJECTED);
        when(eventStartupRepository.findByEventId("e1")).thenReturn(java.util.List.of());
        when(startupRepository.findAllById(any())).thenReturn(java.util.List.of(removed, rejected));

        assertThatThrownBy(() -> service().updateEvent("organizer1", "e1",
                new UpdateEventRequest(null, null, null, null, null, null, null, null, null, java.util.List.of("removed"))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service().updateEvent("organizer1", "e1",
                new UpdateEventRequest(null, null, null, null, null, null, null, null, null, java.util.List.of("rejected"))))
                .isInstanceOf(BadRequestException.class);
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).save(any());
    }

    // ---- link / unlink one startup ----

    @Test
    void anOrganizerWhoManagesTheStartupCanLinkItOnce() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        userManagesStartup("organizer1", "s1", true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startupNamed("s1")));
        when(eventStartupRepository.existsByEventIdAndStartupId("e1", "s1")).thenReturn(false);
        when(attendeeRepository.countByEventId("e1")).thenReturn(0L);

        service().linkStartup("organizer1", "e1", "s1");

        org.mockito.Mockito.verify(eventStartupRepository).save(any());
    }

    @Test
    void linkingTheSameStartupTwiceIsAConflictAndSavesNothing() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        userManagesStartup("organizer1", "s1", true);
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startupNamed("s1")));
        when(eventStartupRepository.existsByEventIdAndStartupId("e1", "s1")).thenReturn(true);

        assertThatThrownBy(() -> service().linkStartup("organizer1", "e1", "s1"))
                .isInstanceOf(com.nukkad.common.exception.ConflictException.class);
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void someoneWhoDoesNotRunTheEventCannotLinkEvenTheirOwnStartup() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));

        assertThatThrownBy(() -> service().linkStartup("founder2", "e1", "s1")).isInstanceOf(ForbiddenException.class);
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void anOrganizerCannotLinkAStartupTheyDoNotManage() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        userManagesStartup("organizer1", "s1", false);

        assertThatThrownBy(() -> service().linkStartup("organizer1", "e1", "s1")).isInstanceOf(ForbiddenException.class);
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void theOrganizerCanTakeAStartupOffTheEvent() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        com.nukkad.event.entity.EventStartup l = link("e1", "s1");
        when(eventStartupRepository.findByEventIdAndStartupId("e1", "s1")).thenReturn(Optional.of(l));

        service().unlinkStartup("organizer1", "e1", "s1");

        org.mockito.Mockito.verify(eventStartupRepository).delete(l);
    }

    @Test
    void aFounderOrAdminOfTheStartupCanTakeItOffSomeoneElsesEvent() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        userManagesStartup("founder2", "s1", true);
        com.nukkad.event.entity.EventStartup l = link("e1", "s1");
        when(eventStartupRepository.findByEventIdAndStartupId("e1", "s1")).thenReturn(Optional.of(l));

        service().unlinkStartup("founder2", "e1", "s1");

        org.mockito.Mockito.verify(eventStartupRepository).delete(l);
    }

    @Test
    void aStrangerCannotTakeAStartupOffAnEvent() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        userManagesStartup("stranger", "s1", false);

        assertThatThrownBy(() -> service().unlinkStartup("stranger", "e1", "s1")).isInstanceOf(ForbiddenException.class);
        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void takingOffAStartupThatIsNotOnTheEventIsNotFound() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        when(eventStartupRepository.findByEventIdAndStartupId("e1", "s1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().unlinkStartup("organizer1", "e1", "s1"))
                .isInstanceOf(com.nukkad.common.exception.ResourceNotFoundException.class);
    }

    // ---- the event page: who may take a startup off ----

    @Test
    void theEventPageOffersTheUnlinkOnlyToWhoMayUseIt() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        when(attendeeRepository.countByEventId("e1")).thenReturn(0L);
        when(eventStartupRepository.findByEventId("e1")).thenReturn(java.util.List.of(link("e1", "mine"), link("e1", "other")));
        when(startupRepository.findAllById(any())).thenReturn(java.util.List.of(startupNamed("mine"), startupNamed("other")));
        when(startupTeamMemberRepository.findByUserIdAndTeamRoleInAndStatus(org.mockito.ArgumentMatchers.eq("founder2"), any(), any()))
                .thenReturn(java.util.List.of(com.nukkad.startup.entity.StartupTeamMember.builder().startupId("mine").userId("founder2")
                        .teamRole(com.nukkad.startup.entity.StartupTeamMember.TeamRole.FOUNDER)
                        .status(com.nukkad.startup.entity.StartupTeamMember.Status.ACTIVE).build()));

        var asFounder = service().getEvent("e1", "founder2").startups();
        var asOrganizer = service().getEvent("e1", "organizer1").startups();
        var asStranger = service().getEvent("e1", "stranger").startups();

        assertThat(asFounder).extracting("id", "canUnlink").containsExactly(
                org.assertj.core.groups.Tuple.tuple("mine", true), org.assertj.core.groups.Tuple.tuple("other", false));
        assertThat(asOrganizer).extracting("canUnlink").containsExactly(true, true);
        assertThat(asStranger).extracting("canUnlink").containsExactly(false, false);
    }

    // ---- a startup's events ----

    @Test
    void aStartupsEventsCarryTheImageTheChapterAndWhereTheyAreInTime() {
        Instant now = Instant.now();
        Event upcoming = Event.builder().id("up").title("Demo Day").chapterId("c1").coverImageUrl("http://img/up.png")
                .startAt(now.plus(3, ChronoUnit.DAYS)).endAt(now.plus(3, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS)).build();
        Event live = Event.builder().id("live").title("Hack").startAt(now.minus(1, ChronoUnit.HOURS)).endAt(now.plus(1, ChronoUnit.HOURS)).build();
        Event ended = Event.builder().id("ended").title("Old meetup").startAt(now.minus(9, ChronoUnit.DAYS)).endAt(now.minus(9, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)).build();
        when(startupRepository.findById("s1")).thenReturn(Optional.of(startupNamed("s1")));
        when(eventStartupRepository.findByStartupId("s1")).thenReturn(java.util.List.of(link("up", "s1"), link("live", "s1"), link("ended", "s1")));
        when(eventRepository.findAllById(any())).thenReturn(java.util.List.of(upcoming, live, ended));
        when(chapterRepository.findAllById(java.util.List.of("c1"))).thenReturn(java.util.List.of(chapter("c1", "p1")));

        var result = service().getEventsForStartup("s1", "member1");

        assertThat(result).extracting("id", "status").containsExactly(
                org.assertj.core.groups.Tuple.tuple("ended", com.nukkad.event.entity.EventStatus.ENDED),
                org.assertj.core.groups.Tuple.tuple("live", com.nukkad.event.entity.EventStatus.LIVE),
                org.assertj.core.groups.Tuple.tuple("up", com.nukkad.event.entity.EventStatus.UPCOMING));
        var demo = result.stream().filter(e -> e.id().equals("up")).findFirst().orElseThrow();
        assertThat(demo.coverImageUrl()).isEqualTo("http://img/up.png");
        assertThat(demo.chapterName()).isEqualTo("Nukkad Bengaluru");
        assertThat(result.stream().filter(e -> e.id().equals("live")).findFirst().orElseThrow().chapterName()).isNull();
    }

    @Test
    void omittingStartupIdsOnUpdateLeavesExistingAssociationsUntouched() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId("e1")).thenReturn(0L);
        when(eventStartupRepository.findByEventId("e1")).thenReturn(java.util.List.of());

        service().updateEvent("organizer1", "e1",
                new UpdateEventRequest("New title", null, null, null, null, null, null, null, null, null));

        org.mockito.Mockito.verify(eventStartupRepository, org.mockito.Mockito.never()).deleteByEventId(any());
    }

    @Test
    void getEventsForStartupReturnsEventsSortedByStartTime() {
        Instant laterStart = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant soonerStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Event later = Event.builder().id("e-later").title("Later").startAt(laterStart).endAt(laterStart.plus(1, ChronoUnit.HOURS)).build();
        Event sooner = Event.builder().id("e-sooner").title("Sooner").startAt(soonerStart).endAt(soonerStart.plus(1, ChronoUnit.HOURS)).build();
        when(eventStartupRepository.findByStartupId("s1")).thenReturn(java.util.List.of(
                com.nukkad.event.entity.EventStartup.builder().eventId("e-later").startupId("s1").build(),
                com.nukkad.event.entity.EventStartup.builder().eventId("e-sooner").startupId("s1").build()));
        when(eventRepository.findAllById(java.util.List.of("e-later", "e-sooner"))).thenReturn(java.util.List.of(later, sooner));
        when(startupRepository.findById("s1")).thenReturn(java.util.Optional.of(
                com.nukkad.startup.entity.Startup.builder().id("s1").name("Ledgerly").build()));

        var result = service().getEventsForStartup("s1", "member1");

        assertThat(result).extracting("id").containsExactly("e-sooner", "e-later");
    }

    @Test
    void theEventsOfARemovedStartupAreNotListedForAnyone() {
        com.nukkad.startup.entity.Startup removed = com.nukkad.startup.entity.Startup.builder().id("s1").name("Ledgerly").build();
        removed.setRemovedByAdmin(true);
        when(startupRepository.findById("s1")).thenReturn(java.util.Optional.of(removed));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().getEventsForStartup("s1", "member1"))
                .isInstanceOf(com.nukkad.common.exception.ResourceNotFoundException.class);
    }

    @Test
    void theEventsOfARejectedStartupAreHiddenFromAStrangerButNotFromItsFounder() {
        com.nukkad.startup.entity.Startup rejected = com.nukkad.startup.entity.Startup.builder().id("s1").name("Ledgerly").build();
        rejected.setModerationStatus(com.nukkad.common.moderation.ModerationStatus.REJECTED);
        when(startupRepository.findById("s1")).thenReturn(java.util.Optional.of(rejected));
        when(startupTeamMemberRepository.findByStartupIdAndUserId("s1", "stranger1")).thenReturn(java.util.Optional.empty());
        when(startupTeamMemberRepository.findByStartupIdAndUserId("s1", "founder1")).thenReturn(java.util.Optional.of(
                com.nukkad.startup.entity.StartupTeamMember.builder().startupId("s1").userId("founder1")
                        .teamRole(com.nukkad.startup.entity.StartupTeamMember.TeamRole.FOUNDER)
                        .status(com.nukkad.startup.entity.StartupTeamMember.Status.ACTIVE).build()));
        when(eventStartupRepository.findByStartupId("s1")).thenReturn(java.util.List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().getEventsForStartup("s1", "stranger1"))
                .isInstanceOf(com.nukkad.common.exception.ResourceNotFoundException.class);
        assertThat(service().getEventsForStartup("s1", "founder1")).isEmpty();
    }

    @Test
    void anEventPageOnlyNamesTheStartupsTheViewerMayRead() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Event event = Event.builder().id("e1").title("Demo night").startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).build();
        when(eventRepository.findById("e1")).thenReturn(java.util.Optional.of(event));
        com.nukkad.startup.entity.Startup live = com.nukkad.startup.entity.Startup.builder().id("s-live").name("Live Co").build();
        com.nukkad.startup.entity.Startup removed = com.nukkad.startup.entity.Startup.builder().id("s-removed").name("Removed Co").build();
        removed.setRemovedByAdmin(true);
        com.nukkad.startup.entity.Startup rejected = com.nukkad.startup.entity.Startup.builder().id("s-rejected").name("Rejected Co").build();
        rejected.setModerationStatus(com.nukkad.common.moderation.ModerationStatus.REJECTED);
        when(eventStartupRepository.findByEventId("e1")).thenReturn(java.util.List.of(
                com.nukkad.event.entity.EventStartup.builder().eventId("e1").startupId("s-live").build(),
                com.nukkad.event.entity.EventStartup.builder().eventId("e1").startupId("s-removed").build(),
                com.nukkad.event.entity.EventStartup.builder().eventId("e1").startupId("s-rejected").build()));
        when(startupRepository.findAllById(java.util.List.of("s-live", "s-removed", "s-rejected")))
                .thenReturn(java.util.List.of(live, removed, rejected));
        when(startupTeamMemberRepository.findByStartupIdAndUserId("s-rejected", "member1")).thenReturn(java.util.Optional.empty());

        EventDto dto = service().getEvent("e1", "member1");

        assertThat(dto.startups()).extracting("id").containsExactly("s-live");
    }

    // ---- cover image upload -------------------------------------------------------------------------

    @Test
    void uploadingACoverImageStoresItUnderEventCoversAndReturnsItsUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[] {1, 2, 3});
        when(fileStorageService.storeImage(file, "event-covers")).thenReturn("https://media.example.com/event-covers/a.png");

        var result = service().uploadCoverImage(file);

        assertThat(result.url()).isEqualTo("https://media.example.com/event-covers/a.png");
    }

    @Test
    void aCoverImageOverTheSizeLimitIsRejectedWithoutTouchingStorage() {
        MultipartFile tooBig = org.mockito.Mockito.mock(MultipartFile.class);
        when(tooBig.getSize()).thenReturn(EventService.MAX_COVER_IMAGE_BYTES + 1);

        assertThatThrownBy(() -> service().uploadCoverImage(tooBig))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("8 MB");
        org.mockito.Mockito.verifyNoInteractions(fileStorageService);
    }

    @Test
    void aCoverImageExactlyAtTheSizeLimitIsAccepted() {
        MultipartFile atLimit = org.mockito.Mockito.mock(MultipartFile.class);
        when(atLimit.getSize()).thenReturn(EventService.MAX_COVER_IMAGE_BYTES);
        when(fileStorageService.storeImage(atLimit, "event-covers")).thenReturn("https://media.example.com/event-covers/b.jpg");

        assertThat(service().uploadCoverImage(atLimit).url()).isEqualTo("https://media.example.com/event-covers/b.jpg");
    }

    @Test
    void storageRejectionsSuchAsANonImageFilePassStraightThrough() {
        MockMultipartFile pdf = new MockMultipartFile("file", "cover.pdf", "application/pdf", new byte[] {1});
        when(fileStorageService.storeImage(pdf, "event-covers"))
                .thenThrow(new BadRequestException("Only PNG, JPEG, WEBP or GIF images are allowed"));

        assertThatThrownBy(() -> service().uploadCoverImage(pdf))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("images are allowed");
    }

    // ---- QA audit: status, validation and RSVP rules the server itself must enforce ----

    private Event eventBetween(String id, Instant start, Instant end) {
        return Event.builder().id(id).title("Demo night").organizerUserId("organizer1")
                .startAt(start).endAt(end).online(false).location("HSR Layout").build();
    }

    @Test
    void theServerSaysWhereAnEventIsInTimeSoNoClientHasToGuess() {
        Instant now = Instant.now();
        Event upcoming = eventBetween("up", now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
        Event live = eventBetween("live", now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        Event ended = eventBetween("gone", now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        when(eventRepository.findById("up")).thenReturn(Optional.of(upcoming));
        when(eventRepository.findById("live")).thenReturn(Optional.of(live));
        when(eventRepository.findById("gone")).thenReturn(Optional.of(ended));
        when(attendeeRepository.countByEventId(any())).thenReturn(0L);

        assertThat(service().getEvent("up", "viewer1").status()).isEqualTo(com.nukkad.event.entity.EventStatus.UPCOMING);
        assertThat(service().getEvent("live", "viewer1").status()).isEqualTo(com.nukkad.event.entity.EventStatus.LIVE);
        assertThat(service().getEvent("gone", "viewer1").status()).isEqualTo(com.nukkad.event.entity.EventStatus.ENDED);
    }

    @Test
    void anEventThatHasAlreadyEndedCannotBeCreated() {
        Instant end = Instant.now().minus(1, ChronoUnit.HOURS);
        CreateEventRequest inThePast = new CreateEventRequest("Old news", null, null, end.minus(2, ChronoUnit.HOURS), end,
                false, "Somewhere", null, null, null, null);

        assertThatThrownBy(() -> service().createEvent("regularUser", inThePast)).isInstanceOf(BadRequestException.class);

        verify0Saves();
    }

    @Test
    void anEventThatIsAlreadyUnderWayCanStillBeCreated() {
        Instant now = Instant.now();
        CreateEventRequest underWay = new CreateEventRequest("Happening now", null, null, now.minus(30, ChronoUnit.MINUTES),
                now.plus(2, ChronoUnit.HOURS), false, "Somewhere", null, null, null, null);
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId(any())).thenReturn(0L);

        assertThat(service().createEvent("regularUser", underWay).status()).isEqualTo(com.nukkad.event.entity.EventStatus.LIVE);
    }

    @Test
    void aMeetingLinkThatIsNotAWebAddressIsRefusedAndNothingIsSaved() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateEventRequest hostile = new CreateEventRequest("Webinar", null, null, start, start.plus(1, ChronoUnit.HOURS),
                true, null, "javascript:alert(document.cookie)", null, null, null);

        assertThatThrownBy(() -> service().createEvent("regularUser", hostile)).isInstanceOf(BadRequestException.class);

        verify0Saves();
    }

    @Test
    void aBareMeetingLinkIsStoredAsAnHttpsAddress() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateEventRequest bare = new CreateEventRequest("Webinar", null, null, start, start.plus(1, ChronoUnit.HOURS),
                true, null, "meet.example.com/abc-defg", null, null, null);
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId(any())).thenReturn(0L);

        assertThat(service().createEvent("regularUser", bare).meetingUrl()).isEqualTo("https://meet.example.com/abc-defg");
    }

    @Test
    void aCoverImageThatIsNotAWebAddressIsRefused() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateEventRequest hostile = new CreateEventRequest("Meetup", null, null, start, start.plus(1, ChronoUnit.HOURS),
                false, "HSR Layout", null, "data:text/html;base64,PHNjcmlwdD4=", null, null);

        assertThatThrownBy(() -> service().createEvent("regularUser", hostile)).isInstanceOf(BadRequestException.class);

        verify0Saves();
    }

    @Test
    void anUpdatedMeetingLinkGetsTheSameCheck() {
        Event online = event("e1", null, "organizer1", null);
        online.setOnline(true);
        online.setMeetingUrl("https://meet.example.com/ok");
        when(eventRepository.findById("e1")).thenReturn(Optional.of(online));

        assertThatThrownBy(() -> service().updateEvent("organizer1", "e1",
                new UpdateEventRequest(null, null, null, null, null, null, "javascript:alert(1)", null, null, null)))
                .isInstanceOf(BadRequestException.class);

        verify0Saves();
    }

    @Test
    void registeringForAnEventThatHasEndedIsRefusedAndNoAttendeeIsCreated() {
        Instant now = Instant.now();
        Event ended = eventBetween("gone", now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        when(eventRepository.findByIdForUpdate("gone")).thenReturn(Optional.of(ended));

        assertThatThrownBy(() -> service().rsvp("member1", "gone")).isInstanceOf(BadRequestException.class);

        org.mockito.Mockito.verify(attendeeRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void registeringForAnEventThatIsUnderWayIsStillAllowed() {
        Instant now = Instant.now();
        Event live = eventBetween("live", now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        when(eventRepository.findByIdForUpdate("live")).thenReturn(Optional.of(live));
        when(attendeeRepository.existsByEventIdAndUserId("live", "member1")).thenReturn(false);
        when(attendeeRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().rsvp("member1", "live");

        org.mockito.Mockito.verify(attendeeRepository).saveAndFlush(any());
    }

    @Test
    void capacityCannotBeCutBelowTheNumberAlreadyRegistered() {
        Event openEvent = event("e1", null, "organizer1", 50);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(openEvent));
        when(attendeeRepository.countByEventId("e1")).thenReturn(12L);

        assertThatThrownBy(() -> service().updateEvent("organizer1", "e1",
                new UpdateEventRequest(null, null, null, null, null, null, null, null, 5, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("12");

        verify0Saves();
    }

    @Test
    void capacityAtOrAboveTheNumberRegisteredIsAccepted() {
        Event openEvent = event("e1", null, "organizer1", 50);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(openEvent));
        when(attendeeRepository.countByEventId("e1")).thenReturn(12L);
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service().updateEvent("organizer1", "e1",
                new UpdateEventRequest(null, null, null, null, null, null, null, null, 12, null)).capacity()).isEqualTo(12);
    }

    // ---- request validation the API enforces regardless of the form in front of it ----

    private static final jakarta.validation.Validator VALIDATOR =
            jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aZeroOrNegativeCapacityIsRejectedByTheRequestValidation() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        for (int bad : new int[] {0, -5}) {
            CreateEventRequest request = new CreateEventRequest("Meetup", null, null, start, start.plus(1, ChronoUnit.HOURS),
                    false, "HSR Layout", null, null, bad, null);
            assertThat(VALIDATOR.validate(request)).extracting(v -> v.getPropertyPath().toString()).containsExactly("capacity");
        }
        assertThat(VALIDATOR.validate(new UpdateEventRequest(null, null, null, null, null, null, null, null, 0, null))).hasSize(1);
    }

    @Test
    void aBlankTitleCannotBeSetOnAnEventButLeavingItOutIsFine() {
        assertThat(VALIDATOR.validate(new UpdateEventRequest("   ", null, null, null, null, null, null, null, null, null)))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("title");
        assertThat(VALIDATOR.validate(new UpdateEventRequest(null, null, null, null, null, null, null, null, null, null))).isEmpty();
        assertThat(VALIDATOR.validate(new UpdateEventRequest("Renamed", null, null, null, null, null, null, null, null, null))).isEmpty();
    }
}
