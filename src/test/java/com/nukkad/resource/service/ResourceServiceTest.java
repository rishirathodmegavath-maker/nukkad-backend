package com.nukkad.resource.service;

import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.resource.dto.ResourceDto;
import com.nukkad.resource.dto.UpdateResourceRequest;
import com.nukkad.resource.entity.Resource;
import com.nukkad.resource.entity.ResourceSave;
import com.nukkad.resource.entity.ResourceType;
import com.nukkad.resource.mapper.ResourceMapper;
import com.nukkad.resource.repository.ResourceRepository;
import com.nukkad.resource.repository.ResourceSaveRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers Resource ownership authorization, the "exactly one of url/file" creation rule, and save toggling. */
@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock private ResourceRepository resourceRepository;
    @Mock private ResourceSaveRepository resourceSaveRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private FileStorageService fileStorageService;

    private final ResourceMapper resourceMapper = new ResourceMapper();

    private ResourceService service() {
        return new ResourceService(resourceRepository, resourceSaveRepository, chapterRepository, resourceMapper, fileStorageService);
    }

    private Resource resource(String id, String uploaderId, String chapterId) {
        return Resource.builder().id(id).title("Pitch Deck Template").description("desc")
                .type(ResourceType.TEMPLATE).url("https://example.com/deck").uploaderUserId(uploaderId)
                .chapterId(chapterId).tags(new java.util.HashSet<>()).build();
    }

    // ---- creation: exactly one of url/file ----

    @Test
    void creatingWithNeitherUrlNorFileIsRejected() {
        assertThatThrownBy(() -> service().createResource("u1", "Title", "desc", "Document", null, null, null, Set.of(), "http://x"))
                .isInstanceOf(BadRequestException.class);
        verify(resourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void creatingWithBothUrlAndFileIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", "content".getBytes());
        assertThatThrownBy(() -> service().createResource("u1", "Title", "desc", "Document", "https://example.com", file, null, Set.of(), "http://x"))
                .isInstanceOf(BadRequestException.class);
        verify(resourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void creatingWithUrlOnlyPersistsThatUrl() {
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = service().createResource("u1", "Startup Checklist", "desc", "Document",
                "https://example.com/checklist", null, null, Set.of("Fundraising"), "http://localhost:8082");

        assertThat(dto.url()).isEqualTo("https://example.com/checklist");
        assertThat(dto.uploaderUserId()).isEqualTo("u1");
        assertThat(dto.canManage()).isTrue();
    }

    @Test
    void creatingWithFileStoresItAndPersistsAHostedUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", "content".getBytes());
        when(fileStorageService.storeMedia(any(), org.mockito.ArgumentMatchers.eq("resources")))
                .thenReturn(new FileStorageService.StoredMedia("resources/abc123.pdf", FileStorageService.AttachmentKind.PDF));
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = service().createResource("u1", "Pitch Deck", "desc", "Document",
                null, file, null, Set.of(), "http://localhost:8082");

        assertThat(dto.url()).isEqualTo("http://localhost:8082/uploads/resources/abc123.pdf");
    }

    @Test
    void unknownResourceTypeIsRejected() {
        assertThatThrownBy(() -> service().createResource("u1", "Title", "desc", "NotAType",
                "https://example.com", null, null, Set.of(), "http://x"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void creatingForANonExistentChapterIsRejected() {
        when(chapterRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().createResource("u1", "Title", "desc", "Document",
                "https://example.com", null, "ghost", Set.of(), "http://x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- ownership authorization ----

    @Test
    void ownerCanUpdateTheirResource() {
        Resource res = resource("r1", "u1", null);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = service().updateResource("u1", "r1", new UpdateResourceRequest("New title", null, null, null, null, null));

        assertThat(dto.title()).isEqualTo("New title");
    }

    @Test
    void nonOwnerCannotUpdateAnotherUsersResource() {
        Resource res = resource("r1", "u1", null);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));

        assertThatThrownBy(() -> service().updateResource("u2", "r1", new UpdateResourceRequest("Hijacked", null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(resourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonOwnerCannotDeleteAnotherUsersResource() {
        Resource res = resource("r1", "u1", null);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));

        assertThatThrownBy(() -> service().deleteResource("u2", "r1")).isInstanceOf(ForbiddenException.class);
        verify(resourceRepository, never()).delete(any(Resource.class));
    }

    @Test
    void ownerCanDeleteTheirResource() {
        Resource res = resource("r1", "u1", null);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));

        service().deleteResource("u1", "r1");

        verify(resourceRepository).delete(res);
    }

    @Test
    void deletedResourceCannotBeFetched() {
        when(resourceRepository.findById("gone")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().getResource("gone", "u1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatingAnUnknownChapterIdIsRejected() {
        Resource res = resource("r1", "u1", null);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));
        when(chapterRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateResource("u1", "r1", new UpdateResourceRequest(null, null, null, null, "ghost", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void blankChapterIdOnUpdateUnassignsTheChapter() {
        Resource res = resource("r1", "u1", "c1");
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = service().updateResource("u1", "r1", new UpdateResourceRequest(null, null, null, null, "", null));

        assertThat(dto.chapterId()).isNull();
    }

    // ---- save/bookmark toggle ----

    @Test
    void togglingSaveTwiceReturnsToUnsaved() {
        Resource res = resource("r1", "u1", null);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));
        when(resourceSaveRepository.findByResourceIdAndUserId("r1", "u2"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ResourceSave.builder().id("s1").resourceId("r1").userId("u2").build()));

        boolean firstToggle = service().toggleSave("u2", "r1");
        boolean secondToggle = service().toggleSave("u2", "r1");

        assertThat(firstToggle).isTrue();
        assertThat(secondToggle).isFalse();
        verify(resourceSaveRepository).save(any(ResourceSave.class));
        verify(resourceSaveRepository).delete(any(ResourceSave.class));
    }
}
