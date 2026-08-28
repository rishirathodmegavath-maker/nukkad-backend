package com.nukkad.chapter.service;

import com.nukkad.chapter.dto.ChapterDto;
import com.nukkad.chapter.dto.CreateChapterRequest;
import com.nukkad.chapter.dto.UpdateChapterRequest;
import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.mapper.ChapterMapper;
import com.nukkad.chapter.repository.ChapterRepository;
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
}
