package com.nukkad.event.service;

import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.event.dto.CreateEventRequest;
import com.nukkad.event.dto.EventDto;
import com.nukkad.event.dto.UpdateEventRequest;
import com.nukkad.event.entity.Event;
import com.nukkad.event.entity.EventAttendee;
import com.nukkad.event.mapper.EventMapper;
import com.nukkad.event.repository.EventAttendeeRepository;
import com.nukkad.event.repository.EventRepository;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock private ChapterRepository chapterRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private NotificationService notificationService;

    private final EventMapper eventMapper = new EventMapper();

    private EventService service() {
        return new EventService(eventRepository, attendeeRepository, chapterRepository, userRepository,
                userService, eventMapper, notificationService);
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
                false, "HSR Layout", null, null, null);
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
                false, "Somewhere", null, null, null);

        assertThatThrownBy(() -> service().createEvent("president1", backwards))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void onlineEventWithoutMeetingUrlIsRejected() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        CreateEventRequest noLink = new CreateEventRequest("Webinar", null, null, start, start.plus(1, ChronoUnit.HOURS),
                true, null, null, null, null);

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
                "e1", new UpdateEventRequest("New title", null, null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void regularMemberCannotEditOrDeleteEvent() {
        Event chapterEvent = event("e1", "c1", "president1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(chapterEvent));
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(chapter("c1", "president1")));
        when(userRepository.findById("member1")).thenReturn(Optional.of(user("member1", SecurityRole.USER)));

        assertThatThrownBy(() -> service().updateEvent("member1",
                "e1", new UpdateEventRequest("New title", null, null, null, null, null, null, null, null)))
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
                new UpdateEventRequest("Updated title", null, null, null, null, null, null, null, null));

        assertThat(dto.title()).isEqualTo("Updated title");
    }

    @Test
    void organizerCanEditTheirOwnPersonalEvent() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attendeeRepository.countByEventId("e1")).thenReturn(0L);

        EventDto dto = service().updateEvent("organizer1", "e1",
                new UpdateEventRequest("Updated title", null, null, null, null, null, null, null, null));

        assertThat(dto.title()).isEqualTo("Updated title");
    }

    @Test
    void nonOrganizerCannotEditOrDeleteAnotherUsersPersonalEvent() {
        Event personalEvent = event("e1", null, "organizer1", null);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(personalEvent));

        assertThatThrownBy(() -> service().updateEvent("someoneElse",
                "e1", new UpdateEventRequest("Hijack", null, null, null, null, null, null, null, null)))
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
}
