package com.nukkad.resource.service;

import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.resource.dto.ResourceDto;
import com.nukkad.resource.dto.UpdateResourceRequest;
import com.nukkad.resource.entity.Resource;
import com.nukkad.resource.entity.ResourceCategory;
import com.nukkad.resource.entity.ResourceSave;
import com.nukkad.resource.entity.ResourceType;
import com.nukkad.resource.mapper.ResourceMapper;
import com.nukkad.resource.repository.ResourceRepository;
import com.nukkad.resource.repository.ResourceSaveRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the admin-curated resource library: the "exactly one of url/file" creation rule, audit logging
 * of every admin change, cleanup of a deleted resource's stored file, the download / open-in-browser
 * metadata, and save toggling. Who is allowed to call the admin operations is NOT decided here — it is
 * the /api/admin/** security rule plus ResourceControllerReadOnlyTest.
 */
@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    private static final String HOSTED_PDF = "https://storage.example.com/resources/abc123.pdf";

    @Mock private ResourceRepository resourceRepository;
    @Mock private ResourceSaveRepository resourceSaveRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditService auditService;

    private final ResourceMapper resourceMapper = new ResourceMapper();

    private ResourceService service() {
        return new ResourceService(resourceRepository, resourceSaveRepository, chapterRepository, resourceMapper,
                fileStorageService, auditService);
    }

    private Resource resource(String id, String url) {
        return Resource.builder().id(id).title("Pitch Deck Template").description("desc")
                .type(ResourceType.TEMPLATE).url(url).uploaderUserId("admin1")
                .tags(new java.util.HashSet<>()).build();
    }


    /** Adds a resource the way the admin controller does, with no thumbnail and none of the catalogue fields. */
    private ResourceDto create(String title, String description, String type, String url,
                               org.springframework.web.multipart.MultipartFile file, String chapterId, Set<String> tags) {
        return service().createResource("admin1",
                new ResourceService.NewResource(title, description, type, url, null, null, null, false, chapterId, tags),
                file, null, "1.2.3.4");
    }

    private static UpdateResourceRequest update(String title, String description, String type, String url, String chapterId, Set<String> tags) {
        return new UpdateResourceRequest(title, description, type, null, null, null, null, url, chapterId, tags);
    }

    private ResourceDto createFull(ResourceService.NewResource in, org.springframework.web.multipart.MultipartFile file,
                                   org.springframework.web.multipart.MultipartFile thumbnail) {
        return service().createResource("admin1", in, file, thumbnail, "1.2.3.4");
    }

    private static ResourceService.NewResource linkWith(String category, String provider, Integer minutes, boolean featured) {
        return new ResourceService.NewResource("How to Build an MVP", "desc", "Video", "https://youtube.com/watch?v=abc",
                category, provider, minutes, featured, null, Set.of("Beginner"));
    }

    // ---- creation: exactly one of url/file ----

    @Test
    void creatingWithNeitherUrlNorFileIsRejected() {
        assertThatThrownBy(() -> create("Title", "desc", "Document", null, null, null, Set.of()))
                .isInstanceOf(BadRequestException.class);
        verify(resourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void creatingWithBothUrlAndFileIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", "content".getBytes());
        assertThatThrownBy(() -> create("Title", "desc", "Document", "https://example.com", file, null, Set.of()))
                .isInstanceOf(BadRequestException.class);
        verify(resourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void creatingWithUrlOnlyPersistsThatUrlAndRecordsTheAdmin() {
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = create("Startup Checklist", "desc", "Document",
                "https://example.com/checklist", null, null, Set.of("Fundraising"));

        assertThat(dto.url()).isEqualTo("https://example.com/checklist");
        assertThat(dto.uploaderUserId()).isEqualTo("admin1");
        assertThat(dto.fileName()).as("a link has nothing to download").isNull();
        assertThat(dto.previewable()).isFalse();
    }

    @Test
    void creatingWithAFileStoresItThroughTheResourceUploaderAndPersistsAHostedUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", "content".getBytes());
        when(fileStorageService.storeResourceFile(any(), eq("resources"))).thenReturn(HOSTED_PDF);
        when(fileStorageService.isHostedUrl(HOSTED_PDF)).thenReturn(true);
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = create("Pitch Deck", "desc", "Document", null, file, null, Set.of());

        assertThat(dto.url()).isEqualTo(HOSTED_PDF);
        assertThat(dto.fileName()).isEqualTo("Pitch Deck.pdf");
        assertThat(dto.previewable()).isTrue();
    }

    @Test
    void everyAdminCreationIsAuditLogged() {
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        create("Checklist", "desc", "Document", "https://example.com/c", null, null, Set.of());

        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_RESOURCE_CREATED), eq("Resource"), any(), eq("1.2.3.4"), anyMap());
    }

    @Test
    void creatingWithASchemelessUrlNormalizesItToHttps() {
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = create("Notion Doc", "desc", "Document", "notion.so/doc/abc", null, null, Set.of());

        assertThat(dto.url()).isEqualTo("https://notion.so/doc/abc");
    }

    @Test
    void creatingWithAnAlreadyAbsoluteUrlIsLeftUnchanged() {
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = create("Figma File", "desc", "Document", "http://figma.com/file/x", null, null, Set.of());

        assertThat(dto.url()).isEqualTo("http://figma.com/file/x");
    }

    @Test
    void unknownResourceTypeIsRejected() {
        assertThatThrownBy(() -> create("Title", "desc", "NotAType", "https://example.com", null, null, Set.of()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void creatingForANonExistentChapterIsRejected() {
        when(chapterRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> create("Title", "desc", "Document", "https://example.com", null, "ghost", Set.of()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- catalogue fields: category, provider, duration, featured, thumbnail ----

    @Test
    void catalogueFieldsAreStoredAndReturned() {
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = createFull(linkWith("free-learning", "  Y Combinator ", 32, true), null, null);

        assertThat(dto.category()).isEqualTo("free-learning");
        assertThat(dto.provider()).isEqualTo("Y Combinator");
        assertThat(dto.durationMinutes()).isEqualTo(32);
        assertThat(dto.featured()).isTrue();
        assertThat(dto.thumbnailUrl()).isNull();
    }

    @Test
    void aResourceWithNoCategoryIsAllowed() {
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = createFull(linkWith("", "", 0, false), null, null);

        assertThat(dto.category()).isNull();
        assertThat(dto.provider()).isNull();
        assertThat(dto.durationMinutes()).as("0 means no duration").isNull();
    }

    @Test
    void anUnknownCategoryIsRejectedBeforeAnythingIsStored() {
        assertThatThrownBy(() -> createFull(linkWith("premium-stuff", null, null, false), null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("category");
        verify(resourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void anAbsurdDurationIsRejected() {
        assertThatThrownBy(() -> createFull(linkWith("templates", null, 99999, false), null, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> createFull(linkWith("templates", null, -5, false), null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void anUploadedThumbnailIsStoredAsAnImageAndReturned() {
        MockMultipartFile image = new MockMultipartFile("thumbnail", "cover.png", "image/png", "png".getBytes());
        when(fileStorageService.storeImage(any(), eq("resource-thumbnails"))).thenReturn("https://storage.example.com/resource-thumbnails/t1.png");
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = createFull(linkWith("free-learning", null, null, false), null, image);

        assertThat(dto.thumbnailUrl()).isEqualTo("https://storage.example.com/resource-thumbnails/t1.png");
    }

    @Test
    void aRejectedThumbnailAlsoRemovesTheAlreadyStoredContentFile() {
        MockMultipartFile deck = new MockMultipartFile("file", "deck.pdf", "application/pdf", "pdf".getBytes());
        MockMultipartFile badImage = new MockMultipartFile("thumbnail", "cover.bmp", "image/bmp", "bmp".getBytes());
        when(fileStorageService.storeResourceFile(any(), eq("resources"))).thenReturn(HOSTED_PDF);
        when(fileStorageService.storeImage(any(), eq("resource-thumbnails"))).thenThrow(new BadRequestException("Only PNG, JPEG, WEBP or GIF images are allowed"));

        ResourceService.NewResource in = new ResourceService.NewResource("Deck", "d", "Document", null, "templates", null, null, false, null, Set.of());
        assertThatThrownBy(() -> createFull(in, deck, badImage)).isInstanceOf(BadRequestException.class);

        verify(fileStorageService).deleteIfHosted(HOSTED_PDF);
        verify(resourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatingCatalogueFieldsChangesThemAndBlankOrZeroClearsThem() {
        Resource res = resource("r1", "https://example.com/deck");
        res.setCategory(com.nukkad.resource.entity.ResourceCategory.TEMPLATES);
        res.setProvider("Old source");
        res.setDurationMinutes(10);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto changed = service().updateResource("admin1", "r1",
                new UpdateResourceRequest(null, null, null, "playbooks", "YC", 25, true, null, null, null), "1.2.3.4");
        assertThat(changed.category()).isEqualTo("playbooks");
        assertThat(changed.provider()).isEqualTo("YC");
        assertThat(changed.durationMinutes()).isEqualTo(25);
        assertThat(changed.featured()).isTrue();

        ResourceDto cleared = service().updateResource("admin1", "r1",
                new UpdateResourceRequest(null, null, null, "", "", 0, false, null, null, null), "1.2.3.4");
        assertThat(cleared.category()).isNull();
        assertThat(cleared.provider()).isNull();
        assertThat(cleared.durationMinutes()).isNull();
        assertThat(cleared.featured()).isFalse();
    }

    @Test
    void updatingWithNullsLeavesTheCatalogueFieldsAlone() {
        Resource res = resource("r1", "https://example.com/deck");
        res.setCategory(com.nukkad.resource.entity.ResourceCategory.TOOLS);
        res.setFeatured(true);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = service().updateResource("admin1", "r1", update("Renamed", null, null, null, null, null), "1.2.3.4");

        assertThat(dto.category()).isEqualTo("tools");
        assertThat(dto.featured()).isTrue();
    }

    @Test
    void updatingWithAnUnknownCategoryIsRejected() {
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(resource("r1", "https://example.com/deck")));
        assertThatThrownBy(() -> service().updateResource("admin1", "r1",
                new UpdateResourceRequest(null, null, null, "nope", null, null, null, null, null, null), "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void replacingTheThumbnailStoresTheNewImageThenRemovesTheOldOne() {
        String old = "https://storage.example.com/resource-thumbnails/old.png";
        String fresh = "https://storage.example.com/resource-thumbnails/new.png";
        Resource res = resource("r1", "https://example.com/deck");
        res.setThumbnailUrl(old);
        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", "png".getBytes());
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));
        when(fileStorageService.storeImage(any(), eq("resource-thumbnails"))).thenReturn(fresh);
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = service().replaceThumbnail("admin1", "r1", image, "1.2.3.4");

        assertThat(dto.thumbnailUrl()).isEqualTo(fresh);
        InOrder order = inOrder(fileStorageService, resourceRepository);
        order.verify(fileStorageService).storeImage(any(), eq("resource-thumbnails"));
        order.verify(resourceRepository).saveAndFlush(res);
        order.verify(fileStorageService).deleteIfHosted(old);
        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_RESOURCE_UPDATED), eq("Resource"), eq("r1"), eq("1.2.3.4"), anyMap());
    }

    @Test
    void removingTheThumbnailClearsItAndDeletesTheHostedImage() {
        String old = "https://storage.example.com/resource-thumbnails/old.png";
        Resource res = resource("r1", "https://example.com/deck");
        res.setThumbnailUrl(old);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = service().removeThumbnail("admin1", "r1", "1.2.3.4");

        assertThat(dto.thumbnailUrl()).isNull();
        verify(fileStorageService).deleteIfHosted(old);
    }

    @Test
    void deletingAResourceAlsoDeletesItsThumbnailImage() {
        String thumb = "https://storage.example.com/resource-thumbnails/t.png";
        Resource res = resource("r1", HOSTED_PDF);
        res.setThumbnailUrl(thumb);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));

        service().deleteResource("admin1", "r1", "1.2.3.4");

        verify(fileStorageService).deleteIfHosted(HOSTED_PDF);
        verify(fileStorageService).deleteIfHosted(thumb);
    }

    @Test
    void theNewLibraryFormatsAreAcceptedAsTypes() {
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        for (String type : new String[]{"Article", "Guide", "Course", "Tool", "Deck"}) {
            ResourceDto dto = create("T", "d", type, "https://example.com/x", null, null, Set.of());
            assertThat(dto.type()).isEqualTo(type);
        }
    }

    // ---- update ----

    @Test
    void updatingChangesTheFieldsAndIsAuditLogged() {
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(resource("r1", "https://example.com/deck")));
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = service().updateResource("admin1", "r1", update("New title", null, null, null, null, null), "1.2.3.4");

        assertThat(dto.title()).isEqualTo("New title");
        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_RESOURCE_UPDATED), eq("Resource"), eq("r1"), eq("1.2.3.4"), anyMap());
    }

    @Test
    void updatingWithASchemelessUrlNormalizesItToHttps() {
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(resource("r1", "https://example.com/deck")));
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = service().updateResource("admin1", "r1", update(null, null, null, "example.com/updated", null, null), "1.2.3.4");

        assertThat(dto.url()).isEqualTo("https://example.com/updated");
    }

    @Test
    void updatingAnUnknownChapterIdIsRejected() {
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(resource("r1", "https://example.com/deck")));
        when(chapterRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateResource("admin1", "r1", update(null, null, null, null, "ghost", null), "1.2.3.4"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void blankChapterIdOnUpdateUnassignsTheChapter() {
        Resource res = resource("r1", "https://example.com/deck");
        res.setChapterId("c1");
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));
        when(resourceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceDto dto = service().updateResource("admin1", "r1", update(null, null, null, null, "", null), "1.2.3.4");

        assertThat(dto.chapterId()).isNull();
    }

    @Test
    void updatingAnUnknownResourceIsNotFound() {
        when(resourceRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().updateResource("admin1", "ghost", update("x", null, null, null, null, null), "1.2.3.4"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(auditService, never()).log(any(), any(), any(), any(), any(), anyMap());
    }

    // ---- delete ----

    @Test
    void deletingRemovesTheRecordAuditLogsItAndThenDeletesTheStoredFile() {
        Resource res = resource("r1", HOSTED_PDF);
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));

        service().deleteResource("admin1", "r1", "1.2.3.4");

        InOrder order = inOrder(resourceRepository, auditService, fileStorageService);
        order.verify(resourceRepository).delete(res);
        order.verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_RESOURCE_DELETED), eq("Resource"), eq("r1"), eq("1.2.3.4"), anyMap());
        order.verify(fileStorageService).deleteIfHosted(HOSTED_PDF); // the file is only removed once the record is gone
    }

    @Test
    void deletingAnUnknownResourceIsNotFoundAndTouchesNothing() {
        when(resourceRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deleteResource("admin1", "ghost", "1.2.3.4")).isInstanceOf(ResourceNotFoundException.class);

        verify(resourceRepository, never()).delete(any(Resource.class));
        verify(fileStorageService, never()).deleteIfHosted(any());
    }

    @Test
    void deletedResourceCannotBeFetched() {
        when(resourceRepository.findById("gone")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().getResource("gone", "u1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void bulkDeleteRemovesEveryRequestedResourceAndAuditLogsEachOne() {
        Resource a = resource("r1", HOSTED_PDF);
        Resource b = resource("r2", "https://example.com/link");
        when(resourceRepository.findAllById(Set.of("r1", "r2"))).thenReturn(List.of(a, b));

        service().bulkDeleteResources("admin1", List.of("r1", "r2"), "1.2.3.4");

        verify(resourceRepository).delete(a);
        verify(resourceRepository).delete(b);
        verify(fileStorageService).deleteIfHosted(HOSTED_PDF);
        verify(auditService, times(2))
                .log(eq("admin1"), eq(AuditAction.ADMIN_RESOURCE_DELETED), eq("Resource"), any(), eq("1.2.3.4"), anyMap());
    }

    @Test
    void bulkDeleteResourcesIsAllOrNothingIfAnyRequestedIdDoesNotExist() {
        Resource a = resource("r1", HOSTED_PDF);
        when(resourceRepository.findAllById(Set.of("r1", "ghost"))).thenReturn(List.of(a));

        assertThatThrownBy(() -> service().bulkDeleteResources("admin1", List.of("r1", "ghost"), null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(resourceRepository, never()).delete(any(Resource.class));
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void bulkDeleteResourcesRejectsAnEmptyIdList() {
        assertThatThrownBy(() -> service().bulkDeleteResources("admin1", List.of(), null))
                .isInstanceOf(BadRequestException.class);
        verify(resourceRepository, never()).findAllById(any());
    }

    // ---- open in browser / download metadata ----

    @Test
    void aHostedPdfIsPreviewableAndAHostedWordFileIsDownloadOnly() {
        String docx = "https://storage.example.com/resources/def456.docx";
        when(resourceRepository.findById("pdf")).thenReturn(Optional.of(resource("pdf", HOSTED_PDF)));
        when(resourceRepository.findById("doc")).thenReturn(Optional.of(resource("doc", docx)));
        when(fileStorageService.isHostedUrl(HOSTED_PDF)).thenReturn(true);
        when(fileStorageService.isHostedUrl(docx)).thenReturn(true);

        ResourceDto pdf = service().getResource("pdf", "u1");
        ResourceDto doc = service().getResource("doc", "u1");

        assertThat(pdf.previewable()).isTrue();
        assertThat(doc.previewable()).as("browsers cannot render Word files inline").isFalse();
        assertThat(doc.fileName()).isEqualTo("Pitch Deck Template.docx");
    }

    @Test
    void theDownloadNameIsFilesystemSafe() {
        Resource res = resource("r1", HOSTED_PDF);
        res.setTitle("Q3/Report: \"Final\" <draft>?*");
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(res));
        when(fileStorageService.isHostedUrl(HOSTED_PDF)).thenReturn(true);

        String name = service().getResource("r1", "u1").fileName();

        assertThat(name).endsWith(".pdf").doesNotContain("/", "\\", ":", "\"", "<", ">", "?", "*");
    }

    @Test
    void downloadingALinkResourceIsRejected() {
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(resource("r1", "https://example.com/deck")));

        assertThatThrownBy(() -> service().openDownload("r1")).isInstanceOf(BadRequestException.class);
        verify(fileStorageService, never()).open(any());
    }

    @Test
    void downloadingAHostedFileStreamsItWithItsSavedAsName() throws Exception {
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(resource("r1", HOSTED_PDF)));
        when(fileStorageService.isHostedUrl(HOSTED_PDF)).thenReturn(true);
        InputStream bytes = new ByteArrayInputStream("pdf-bytes".getBytes());
        when(fileStorageService.open(HOSTED_PDF)).thenReturn(new FileStorageService.StoredObject(bytes, "application/pdf", 9));

        ResourceService.Download download = service().openDownload("r1");

        assertThat(download.fileName()).isEqualTo("Pitch Deck Template.pdf");
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.contentLength()).isEqualTo(9);
        assertThat(new String(download.stream().readAllBytes())).isEqualTo("pdf-bytes");
    }

    @Test
    void downloadingAnUnknownResourceIsNotFound() {
        when(resourceRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().openDownload("ghost")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminChapterOptionsAreTheChaptersByName() {
        when(chapterRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.List.of(com.nukkad.chapter.entity.Chapter.builder().id("c1").name("Bengaluru").build()));

        assertThat(service().listChapterOptions())
                .containsExactly(new com.nukkad.resource.dto.ResourceChapterOption("c1", "Bengaluru"));
    }

    // ---- save/bookmark toggle (the one thing a member may still do to a resource) ----

    @Test
    void togglingSaveTwiceReturnsToUnsaved() {
        when(resourceRepository.findById("r1")).thenReturn(Optional.of(resource("r1", "https://example.com/deck")));
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

    // ---- browse: "All" (unfiltered) vs a category/type-filtered view --------------------------------

    @Test
    void categoryAllBehavesTheSameAsNoCategoryFilterInsteadOfBeingRejected() {
        when(resourceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(item("v1", ResourceCategory.VIDEOS, ResourceType.VIDEO, 1, false)));

        Page<ResourceDto> result = service().listResources(null, null, "all", null, null, "u1", 0, 20);

        assertThat(result.getContent()).extracting(ResourceDto::id).containsExactly("v1");
    }

    @Test
    void anUnknownCategoryFilterIsStillRejected() {
        assertThatThrownBy(() -> service().listResources(null, null, "not-a-shelf", null, null, "u1", 0, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void anUnknownTypeFilterIsRejectedAsABadRequestNotARawIllegalArgument() {
        assertThatThrownBy(() -> service().listResources(null, "Podcast", null, null, null, "u1", 0, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void theUnfilteredAllBrowseInterleavesTypesInsteadOfShowingOneTypeAtATime() {
        // Plain newest-first alone would be l1, l2, l3, v1, d1 — three links, then everything else.
        when(resourceRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page(
                item("l1", null, ResourceType.LINK, 1, false),
                item("l2", null, ResourceType.LINK, 2, false),
                item("l3", null, ResourceType.LINK, 3, false),
                item("v1", null, ResourceType.VIDEO, 4, false),
                item("d1", null, ResourceType.DECK, 5, false)));

        Page<ResourceDto> result = service().listResources(null, null, null, null, null, "u1", 0, 20);

        assertThat(result.getContent()).extracting(ResourceDto::id).containsExactly("l1", "v1", "d1", "l2", "l3");
        assertThat(result.getTotalElements()).isEqualTo(5);
    }

    @Test
    void aCategoryFilteredBrowseKeepsPlainNewestFirstOrderNotInterleaved() {
        when(resourceRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page(
                item("v1", ResourceCategory.VIDEOS, ResourceType.VIDEO, 1, false),
                item("v2", ResourceCategory.VIDEOS, ResourceType.VIDEO, 2, false)));

        Page<ResourceDto> result = service().listResources(null, null, "videos", null, null, "u1", 0, 20);

        assertThat(result.getContent()).extracting(ResourceDto::id).containsExactly("v1", "v2");
    }

    @Test
    void aTypeFilteredBrowseKeepsPlainNewestFirstOrderNotInterleaved() {
        when(resourceRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page(
                item("l1", null, ResourceType.LINK, 1, false),
                item("l2", null, ResourceType.LINK, 2, false)));

        Page<ResourceDto> result = service().listResources(null, "Link", null, null, null, "u1", 0, 20);

        assertThat(result.getContent()).extracting(ResourceDto::id).containsExactly("l1", "l2");
    }

    @Test
    void pagingTheInterleavedAllBrowseNeverRepeatsOrSkipsAResource() {
        when(resourceRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page(
                item("l1", null, ResourceType.LINK, 1, false),
                item("l2", null, ResourceType.LINK, 2, false),
                item("l3", null, ResourceType.LINK, 3, false),
                item("v1", null, ResourceType.VIDEO, 4, false),
                item("d1", null, ResourceType.DECK, 5, false)));

        Page<ResourceDto> page1 = service().listResources(null, null, null, null, null, "u1", 0, 2);
        Page<ResourceDto> page2 = service().listResources(null, null, null, null, null, "u1", 1, 2);
        Page<ResourceDto> page3 = service().listResources(null, null, null, null, null, "u1", 2, 2);

        List<String> seenInOrder = new java.util.ArrayList<>();
        List.of(page1, page2, page3).forEach(p -> p.getContent().forEach(dto -> seenInOrder.add(dto.id())));
        assertThat(seenInOrder).containsExactly("l1", "v1", "d1", "l2", "l3");
        assertThat(new java.util.HashSet<>(seenInOrder)).hasSize(5);
    }

    // ---- front-page mix -----------------------------------------------------------------------------

    private static Resource item(String id, ResourceCategory category, ResourceType type, long ageMinutes, boolean featured) {
        return Resource.builder().id(id).title("Title " + id).description("d").type(type).category(category)
                .url("https://example.com/" + id).uploaderUserId("admin1").featured(featured)
                .createdAt(Instant.parse("2026-09-20T12:00:00Z").minusSeconds(ageMinutes * 60))
                .tags(new java.util.HashSet<>()).build();
    }

    private static Object[] group(ResourceCategory category, ResourceType type) {
        return new Object[] {category, type};
    }

    /** A list of {category, type} rows. (List.of would spread a single array into its elements.) */
    private static List<Object[]> groups(Object[]... rows) {
        return java.util.Arrays.asList(rows);
    }

    @SuppressWarnings("unchecked")
    private void lanesReturnInOrder(Page<Resource>... pages) {
        var stubbing = when(resourceRepository.findAll(any(Specification.class), any(Pageable.class)));
        var chain = stubbing.thenReturn(pages[0]);
        for (int i = 1; i < pages.length; i++) chain = chain.thenReturn(pages[i]);
    }

    private static Page<Resource> page(Resource... items) {
        return new PageImpl<>(List.of(items));
    }

    private List<String> ids(List<ResourceDto> dtos) {
        return dtos.stream().map(ResourceDto::id).toList();
    }

    @Test
    void theMixTakesOneFromEachShelfAndTypeBeforeGoingRoundAgain() {
        when(resourceRepository.findShelfAndTypeGroups()).thenReturn(groups(
                group(ResourceCategory.PLAYBOOKS, ResourceType.LINK),
                group(ResourceCategory.FREE_LEARNING, ResourceType.VIDEO),
                group(ResourceCategory.TEMPLATES, ResourceType.DOCUMENT)));
        lanesReturnInOrder(
                page(item("e1", ResourceCategory.PLAYBOOKS, ResourceType.LINK, 1, false),
                        item("e2", ResourceCategory.PLAYBOOKS, ResourceType.LINK, 2, false),
                        item("e3", ResourceCategory.PLAYBOOKS, ResourceType.LINK, 3, false)),
                page(item("v1", ResourceCategory.FREE_LEARNING, ResourceType.VIDEO, 10, false)),
                page(item("d1", ResourceCategory.TEMPLATES, ResourceType.DOCUMENT, 20, false)));

        List<ResourceDto> mix = service().mix(4, false, "u1");

        // Newest lane leads each round: essay, video, document, then the second essay.
        assertThat(ids(mix)).containsExactly("e1", "v1", "d1", "e2");
    }

    @Test
    void aBulkUploadOfOneKindCannotCrowdOutEverythingElse() {
        when(resourceRepository.findShelfAndTypeGroups()).thenReturn(groups(
                group(ResourceCategory.PLAYBOOKS, ResourceType.LINK),
                group(ResourceCategory.FREE_LEARNING, ResourceType.VIDEO)));
        lanesReturnInOrder(
                page(item("e1", ResourceCategory.PLAYBOOKS, ResourceType.LINK, 1, false),
                        item("e2", ResourceCategory.PLAYBOOKS, ResourceType.LINK, 2, false),
                        item("e3", ResourceCategory.PLAYBOOKS, ResourceType.LINK, 3, false),
                        item("e4", ResourceCategory.PLAYBOOKS, ResourceType.LINK, 4, false)),
                page(item("v1", ResourceCategory.FREE_LEARNING, ResourceType.VIDEO, 500, false)));

        // Plain newest-first would be e1, e2, e3: three essays and no video.
        assertThat(ids(service().mix(3, false, "u1"))).containsExactly("e1", "v1", "e2");
    }

    @Test
    void whenAskedToPreferFeaturedTheFeaturedGroupLeadsAndTheQueryOrdersFeaturedFirst() {
        when(resourceRepository.findShelfAndTypeGroups()).thenReturn(groups(
                group(ResourceCategory.PLAYBOOKS, ResourceType.LINK),
                group(ResourceCategory.TOOLS, ResourceType.TOOL)));
        lanesReturnInOrder(
                page(item("e1", ResourceCategory.PLAYBOOKS, ResourceType.LINK, 1, false)),
                page(item("t1", ResourceCategory.TOOLS, ResourceType.TOOL, 900, true)));

        assertThat(ids(service().mix(2, true, "u1"))).containsExactly("t1", "e1");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(resourceRepository, times(2)).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("featured")).isNotNull();
        assertThat(pageable.getValue().getSort().getOrderFor("featured").isDescending()).isTrue();
    }

    @Test
    void withoutThePreferenceTheQueryIsPlainlyNewestFirst() {
        when(resourceRepository.findShelfAndTypeGroups()).thenReturn(groups(group(ResourceCategory.TOOLS, ResourceType.TOOL)));
        lanesReturnInOrder(page(item("t1", ResourceCategory.TOOLS, ResourceType.TOOL, 5, false)));

        service().mix(3, false, "u1");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(resourceRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("featured")).isNull();
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void aResourceThatSitsOnNoShelfStillGetsItsOwnLane() {
        when(resourceRepository.findShelfAndTypeGroups()).thenReturn(groups(
                group(ResourceCategory.PLAYBOOKS, ResourceType.LINK),
                group(null, ResourceType.DOCUMENT)));
        lanesReturnInOrder(
                page(item("e1", ResourceCategory.PLAYBOOKS, ResourceType.LINK, 1, false)),
                page(item("n1", null, ResourceType.DOCUMENT, 2, false)));

        assertThat(ids(service().mix(2, false, "u1"))).containsExactly("e1", "n1");
    }

    @Test
    void theMixNeverReturnsMoreThanWasAskedForOrMoreThanExists() {
        when(resourceRepository.findShelfAndTypeGroups()).thenReturn(groups(group(ResourceCategory.TOOLS, ResourceType.TOOL)));
        lanesReturnInOrder(page(item("t1", ResourceCategory.TOOLS, ResourceType.TOOL, 5, false),
                item("t2", ResourceCategory.TOOLS, ResourceType.TOOL, 6, false)));

        assertThat(service().mix(10, false, "u1")).hasSize(2);
    }

    @Test
    void anEmptyLibraryGivesAnEmptyMix() {
        when(resourceRepository.findShelfAndTypeGroups()).thenReturn(groups());

        assertThat(service().mix(6, true, "u1")).isEmpty();
    }

    @Test
    void theRequestedSizeIsClampedToASaneRange() {
        when(resourceRepository.findShelfAndTypeGroups()).thenReturn(groups(group(ResourceCategory.TOOLS, ResourceType.TOOL)));
        lanesReturnInOrder(page(item("t1", ResourceCategory.TOOLS, ResourceType.TOOL, 5, false)));

        service().mix(1000, false, "u1");
        service().mix(-5, false, "u1");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(resourceRepository, times(2)).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getAllValues()).extracting(Pageable::getPageSize).containsExactly(24, 1);
    }
}
