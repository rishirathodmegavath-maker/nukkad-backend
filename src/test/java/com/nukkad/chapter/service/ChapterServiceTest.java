package com.nukkad.chapter.service;

import com.nukkad.chapter.dto.ChapterDto;
import com.nukkad.chapter.dto.CreateChapterRequest;
import com.nukkad.chapter.dto.UpdateChapterRequest;
import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.mapper.ChapterMapper;
import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.event.repository.EventRepository;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.resource.repository.ResourceRepository;
import com.nukkad.opportunity.repository.OpportunityRepository;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers self-serve chapter creation (creator becomes president immediately) and the
 *  president-only update guard. There is no admin/approval step in this flow. */
@ExtendWith(MockitoExtension.class)
class ChapterServiceTest {

    @Mock private ChapterRepository chapterRepository;
    @Mock private UserRepository userRepository;
    @Mock private IdeaRepository ideaRepository;
    @Mock private StartupRepository startupRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @Mock private EventRepository eventRepository;
    @Mock private ResourceRepository resourceRepository;
    @Mock private FileStorageService fileStorageService;

    private final ChapterMapper chapterMapper = new ChapterMapper();
    private final UserMapper userMapper = new UserMapper();

    private ChapterService service() {
        return new ChapterService(chapterRepository, userRepository, ideaRepository, startupRepository,
                opportunityRepository, eventRepository, resourceRepository, chapterMapper, userMapper, fileStorageService);
    }

    private Chapter chapter(String id, String presidentUserId) {
        return Chapter.builder().id(id).name("Nukkad Bengaluru").presidentUserId(presidentUserId).build();
    }

    private User user(String id) {
        return User.builder().id(id).name("User " + id).securityRoles(new HashSet<>(Set.of(SecurityRole.USER))).build();
    }

    @Test
    void creatingAChapterInstallsTheCreatorAsPresidentImmediately() {
        User creator = user("u1");
        when(chapterRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Chapter c = inv.getArgument(0);
            c.setId("c1");
            return c;
        });
        when(userRepository.findById("u1")).thenReturn(Optional.of(creator));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChapterDto dto = service().createChapter("u1", new CreateChapterRequest("Nukkad Pune", "Pune", "India", "A new hub", null));

        assertThat(dto.presidentUserId()).isEqualTo("u1");
        assertThat(creator.getSecurityRoles()).contains(SecurityRole.CHAPTER_PRESIDENT);
    }

    @Test
    void creatingAChapterAutomaticallyJoinsTheCreatorAsAMember() {
        // Regression coverage for the "No members joined yet" QA bug: a brand-new chapter's own
        // president must show up as a member immediately, not only after separately hitting /join.
        User creator = user("u1");
        when(chapterRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Chapter c = inv.getArgument(0);
            c.setId("c1");
            return c;
        });
        when(userRepository.findById("u1")).thenReturn(Optional.of(creator));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().createChapter("u1", new CreateChapterRequest("Nukkad Pune", "Pune", "India", "A new hub", null));

        assertThat(creator.getChapterId()).isEqualTo("c1");
        assertThat(creator.getChapterJoinedAt()).isNotNull();
    }

    @Test
    void creatingAChapterWithAnAlreadyUsedNameIsRejected() {
        when(chapterRepository.existsByNameIgnoreCase("Nukkad Pune")).thenReturn(true);

        assertThatThrownBy(() -> service().createChapter("u1",
                new CreateChapterRequest("Nukkad Pune", "Pune", "India", "A new hub", null)))
                .isInstanceOf(ConflictException.class);
        verify(chapterRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateNameCheckIsCaseAndWhitespaceInsensitive() {
        // The pre-check normalizes via IgnoreCase at the query level and trims before calling it —
        // exercised here by asserting the trimmed name is what's actually looked up.
        when(chapterRepository.existsByNameIgnoreCase("Nukkad Pune")).thenReturn(true);

        assertThatThrownBy(() -> service().createChapter("u1",
                new CreateChapterRequest("  Nukkad Pune  ", null, null, "A new hub", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aConcurrentDuplicateInsertThatRacesPastThePreCheckStillFailsCleanly() {
        // Two simultaneous creates both saw existsByNameIgnoreCase == false before either committed;
        // the loser hits the DB-level uq_chapters_name constraint instead — must still surface as a
        // clean ConflictException, not a raw DataIntegrityViolationException/500.
        when(chapterRepository.existsByNameIgnoreCase("Nukkad Pune")).thenReturn(false);
        when(chapterRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq_chapters_name"));

        assertThatThrownBy(() -> service().createChapter("u1",
                new CreateChapterRequest("Nukkad Pune", "Pune", "India", "A new hub", null)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void presidentCanUpdateTheirOwnChapter() {
        Chapter existing = chapter("c1", "u1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));
        when(chapterRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ChapterDto dto = service().updateChapter("u1", "c1",
                new UpdateChapterRequest("New Name", null, null, null, null));

        assertThat(dto.name()).isEqualTo("New Name");
    }

    @Test
    void nonPresidentCannotUpdateAnotherChapter() {
        Chapter existing = chapter("c1", "u1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().updateChapter("u2", "c1",
                new UpdateChapterRequest("Hijacked", null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);

        verify(chapterRepository, never()).saveAndFlush(any());
    }

    @Test
    void presidentCanAddAMember() {
        Chapter existing = chapter("c1", "u1");
        User target = user("u2");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));
        when(userRepository.findById("u2")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().addMember("u1", "c1", "u2");

        assertThat(target.getChapterId()).isEqualTo("c1");
    }

    @Test
    void nonPresidentCannotAddAMember() {
        Chapter existing = chapter("c1", "u1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().addMember("u2", "c1", "u3"))
                .isInstanceOf(ForbiddenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void presidentCanRemoveAMember() {
        Chapter existing = chapter("c1", "u1");
        User target = user("u2");
        target.setChapterId("c1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));
        when(userRepository.findById("u2")).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().removeMember("u1", "c1", "u2");

        assertThat(target.getChapterId()).isNull();
    }

    @Test
    void presidentCannotRemoveThemselfAsAMember() {
        Chapter existing = chapter("c1", "u1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().removeMember("u1", "c1", "u1"))
                .isInstanceOf(ForbiddenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void presidentCannotLeaveTheirOwnChapter() {
        // leaveChapter is a separate code path from removeMember — without its own guard, a
        // president could leave while chapter.presidentUserId still points at them, orphaning the
        // chapter's presidency with no transfer mechanism to recover it.
        Chapter existing = chapter("c1", "u1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().leaveChapter("u1", "c1"))
                .isInstanceOf(ForbiddenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void nonPresidentMemberCanLeaveTheChapter() {
        Chapter existing = chapter("c1", "u1");
        User member = user("u2");
        member.setChapterId("c1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));
        when(userRepository.findById("u2")).thenReturn(Optional.of(member));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().leaveChapter("u2", "c1");

        assertThat(member.getChapterId()).isNull();
        assertThat(member.getChapterJoinedAt()).isNull();
    }

    @Test
    void nonPresidentCannotDeleteAnotherChapter() {
        Chapter existing = chapter("c1", "u1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().deleteChapter("u2", "c1"))
                .isInstanceOf(ForbiddenException.class);

        verify(chapterRepository, never()).delete(any(Chapter.class));
    }

    @Test
    void presidentCannotDeleteAChapterThatStillHasContentOrOtherMembersAttached() {
        Chapter existing = chapter("c1", "u1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));
        when(ideaRepository.countByChapterId("c1")).thenReturn(1L);

        assertThatThrownBy(() -> service().deleteChapter("u1", "c1"))
                .isInstanceOf(ConflictException.class);

        verify(chapterRepository, never()).delete(any(Chapter.class));
    }

    @Test
    void presidentCanDeleteAnEmptyChapterAndItsOwnDanglingMembershipIsClearedFirst() {
        Chapter existing = chapter("c1", "u1");
        User president = user("u1");
        president.setChapterId("c1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));
        when(userRepository.findById("u1")).thenReturn(Optional.of(president));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().deleteChapter("u1", "c1");

        assertThat(president.getChapterId()).isNull();
        verify(chapterRepository).delete(existing);
    }

    @Test
    void listRecentActivityMergesAcrossEntityTypesAndOrdersByRecency() {
        Chapter existing = chapter("c1", "u1");
        when(chapterRepository.findById("c1")).thenReturn(Optional.of(existing));

        java.time.Instant older = java.time.Instant.parse("2026-01-01T00:00:00Z");
        java.time.Instant newer = java.time.Instant.parse("2026-02-01T00:00:00Z");

        com.nukkad.idea.entity.Idea idea = com.nukkad.idea.entity.Idea.builder()
                .id("i1").title("An idea").creatorId("u2").createdAt(older).build();
        com.nukkad.event.entity.Event event = com.nukkad.event.entity.Event.builder()
                .id("e1").title("Founder Meetup").organizerUserId("u1").createdAt(newer).build();
        when(ideaRepository.findByChapterId(org.mockito.ArgumentMatchers.eq("c1"), any())).thenReturn(java.util.List.of(idea));
        when(eventRepository.findByChapterId(org.mockito.ArgumentMatchers.eq("c1"), any())).thenReturn(java.util.List.of(event));
        when(startupRepository.findByChapterId(org.mockito.ArgumentMatchers.eq("c1"), any())).thenReturn(java.util.List.of());
        when(opportunityRepository.findByChapterId(org.mockito.ArgumentMatchers.eq("c1"), any())).thenReturn(java.util.List.of());
        when(resourceRepository.findByChapterId(org.mockito.ArgumentMatchers.eq("c1"), any())).thenReturn(java.util.List.of());
        when(userRepository.findByChapterIdAndChapterJoinedAtIsNotNull(org.mockito.ArgumentMatchers.eq("c1"), any()))
                .thenReturn(java.util.List.of());
        when(userRepository.findAllById(any())).thenReturn(java.util.List.of(user("u1"), user("u2")));

        java.util.List<com.nukkad.chapter.dto.ChapterActivityDto> activity = service().listRecentActivity("c1", 10);

        assertThat(activity).hasSize(2);
        assertThat(activity.get(0).type()).isEqualTo("EVENT");
        assertThat(activity.get(0).title()).isEqualTo("Founder Meetup");
        assertThat(activity.get(0).actorName()).isEqualTo("User u1");
        assertThat(activity.get(1).type()).isEqualTo("IDEA");
        assertThat(activity.get(1).actorName()).isEqualTo("User u2");
    }
}
