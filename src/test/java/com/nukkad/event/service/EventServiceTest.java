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
                startupRepository, startupTeamMemberRepository, userRepository, userService, eventMapper, notificationService, fileStorageService);
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
        when(eventStartupRepository.findByEventId(any())).thenReturn(java.util.List.of(
                com.nukkad.event.entity.EventStartup.builder().eventId("e1").startupId("s1").build()));

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

    @Test
    void updatingAnEventReplacesTheFullStartupSet() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId("e1")).thenReturn(0L);
        when(startupTeamMemberRepository.existsByStartupIdAndUserIdAndTeamRoleInAndStatus(
                "s2", "organizer1", java.util.List.of(com.nukkad.startup.entity.StartupTeamMember.TeamRole.FOUNDER,
                        com.nukkad.startup.entity.StartupTeamMember.TeamRole.ADMIN),
                com.nukkad.startup.entity.StartupTeamMember.Status.ACTIVE)).thenReturn(true);
        when(eventStartupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventStartupRepository.findByEventId("e1")).thenReturn(java.util.List.of());

        service().updateEvent("organizer1", "e1",
                new UpdateEventRequest(null, null, null, null, null, null, null, null, null, java.util.List.of("s2")));

        org.mockito.Mockito.verify(eventStartupRepository).deleteByEventId("e1");
        org.mockito.Mockito.verify(eventStartupRepository).save(any());
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

        var result = service().getEventsForStartup("s1");

        assertThat(result).extracting("id").containsExactly("e-sooner", "e-later");
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
}
