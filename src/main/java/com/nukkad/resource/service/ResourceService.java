package com.nukkad.resource.service;

import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.resource.dto.ResourceChapterOption;
import com.nukkad.resource.dto.ResourceDto;
import com.nukkad.resource.dto.UpdateResourceRequest;
import com.nukkad.resource.entity.Resource;
import com.nukkad.resource.entity.ResourceCategory;
import com.nukkad.resource.entity.ResourceSave;
import com.nukkad.resource.entity.ResourceType;
import com.nukkad.resource.mapper.ResourceMapper;
import com.nukkad.resource.repository.ResourceRepository;
import com.nukkad.resource.repository.ResourceSaveRepository;
import com.nukkad.resource.repository.ResourceSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The resource library is admin-curated: members can only browse, open, download and save resources.
 * Creating, editing and deleting are admin operations (the admin controller sits under
 * {@code /api/admin/**}, which the security configuration restricts to admin-portal tokens); nothing
 * here checks a role itself, so no member-facing endpoint may ever be wired to those three methods.
 */
@Service
public class ResourceService {

    /** File types a browser can show inline instead of only downloading. */
    private static final Set<String> PREVIEWABLE_EXTENSIONS =
            Set.of("pdf", "png", "jpg", "jpeg", "webp", "gif", "mp4", "webm", "mov", "txt");

    /** What an admin fills in to add a resource; exactly one of the url here or an uploaded file supplies its content. */
    public record NewResource(String title, String description, String type, String url, String category,
                              String provider, Integer durationMinutes, boolean featured, String chapterId,
                              Set<String> tags) {}

    /** A hosted file opened for download. The caller must close {@code stream}. */
    public record Download(String fileName, String contentType, long contentLength, InputStream stream) {}

    private final ResourceRepository resourceRepository;
    private final ResourceSaveRepository resourceSaveRepository;
    private final ChapterRepository chapterRepository;
    private final ResourceMapper resourceMapper;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public ResourceService(ResourceRepository resourceRepository,
                            ResourceSaveRepository resourceSaveRepository,
                            ChapterRepository chapterRepository,
                            ResourceMapper resourceMapper,
                            FileStorageService fileStorageService,
                            AuditService auditService) {
        this.resourceRepository = resourceRepository;
        this.resourceSaveRepository = resourceSaveRepository;
        this.chapterRepository = chapterRepository;
        this.resourceMapper = resourceMapper;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    public Resource getEntityOrThrow(String id) {
        return resourceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Resource not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ResourceDto> listResources(String q, String type, String category, Boolean featured, String chapterId,
                                            String viewerId, int page, int size) {
        Specification<Resource> spec = ResourceSpecifications.combine(
                ResourceSpecifications.search(q),
                ResourceSpecifications.type(type),
                ResourceSpecifications.category(category),
                ResourceSpecifications.featured(featured),
                ResourceSpecifications.chapterId(chapterId)
        );
        // createdAt only has second precision, so break ties on id — otherwise rows created together can repeat or vanish between pages.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return resourceRepository.findAll(spec, pageable).map(r -> toDto(r, viewerId));
    }

    @Transactional(readOnly = true)
    public ResourceDto getResource(String id, String viewerId) {
        return toDto(getEntityOrThrow(id), viewerId);
    }

    /** Chapters an admin can scope a resource to. (The admin portal's token can't call the member chapters API.) */
    @Transactional(readOnly = true)
    public List<ResourceChapterOption> listChapterOptions() {
        return chapterRepository.findAll(Sort.by("name")).stream()
                .map(c -> new ResourceChapterOption(c.getId(), c.getName()))
                .toList();
    }

    /** Opens a hosted file for download. A resource that is a link has nothing to download. */
    @Transactional(readOnly = true)
    public Download openDownload(String id) {
        Resource resource = getEntityOrThrow(id);
        if (!fileStorageService.isHostedUrl(resource.getUrl())) {
            throw new BadRequestException("This resource is a link — open it instead of downloading");
        }
        FileStorageService.StoredObject object = fileStorageService.open(resource.getUrl());
        return new Download(downloadFileName(resource), object.contentType(), object.contentLength(), object.stream());
    }

    // ---- Admin-only operations (see the class comment) ----

    @Transactional
    public ResourceDto createResource(String adminId, NewResource in, MultipartFile file, MultipartFile thumbnail, String ip) {
        ResourceType type = parseType(in.type());
        ResourceCategory category = parseCategory(in.category());
        Integer duration = normalizeDuration(in.durationMinutes());

        boolean hasFile = file != null && !file.isEmpty();
        boolean hasUrl = in.url() != null && !in.url().isBlank();
        if (hasFile == hasUrl) {
            throw new BadRequestException("Provide either a URL or a file to upload — not both, not neither");
        }

        String resolvedChapterId = null;
        if (in.chapterId() != null && !in.chapterId().isBlank()) {
            resolvedChapterId = chapterRepository.findById(in.chapterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + in.chapterId()))
                    .getId();
        }

        String finalUrl = hasFile ? fileStorageService.storeResourceFile(file, "resources") : normalizeUrl(in.url());
        String thumbnailUrl = null;
        if (thumbnail != null && !thumbnail.isEmpty()) {
            try {
                thumbnailUrl = fileStorageService.storeImage(thumbnail, "resource-thumbnails");
            } catch (RuntimeException e) {
                fileStorageService.deleteIfHosted(finalUrl); // don't leave the content file behind for a rejected thumbnail
                throw e;
            }
        }

        Resource resource = Resource.builder()
                .title(in.title().trim())
                .description(in.description())
                .type(type)
                .category(category)
                .provider(blankToNull(in.provider()))
                .thumbnailUrl(thumbnailUrl)
                .durationMinutes(duration)
                .featured(in.featured())
                .url(finalUrl)
                .uploaderUserId(adminId)
                .chapterId(resolvedChapterId)
                .tags(in.tags() == null ? new HashSet<>() : new HashSet<>(in.tags()))
                .build();
        resource = resourceRepository.saveAndFlush(resource);

        auditService.log(adminId, AuditAction.ADMIN_RESOURCE_CREATED, "Resource", resource.getId(), ip,
                Map.of("title", resource.getTitle(), "source", hasFile ? "file" : "link"));
        return toDto(resource, adminId);
    }

    @Transactional
    public ResourceDto updateResource(String adminId, String resourceId, UpdateResourceRequest request, String ip) {
        Resource resource = getEntityOrThrow(resourceId);

        if (request.title() != null) resource.setTitle(request.title());
        if (request.description() != null) resource.setDescription(request.description());
        if (request.type() != null) resource.setType(parseType(request.type()));
        if (request.category() != null) resource.setCategory(parseCategory(request.category()));
        if (request.provider() != null) resource.setProvider(blankToNull(request.provider()));
        if (request.durationMinutes() != null) resource.setDurationMinutes(normalizeDuration(request.durationMinutes()));
        if (request.featured() != null) resource.setFeatured(request.featured());
        if (request.url() != null) {
            if (request.url().isBlank()) throw new BadRequestException("URL cannot be blank");
            resource.setUrl(normalizeUrl(request.url()));
        }
        if (request.chapterId() != null) {
            if (request.chapterId().isBlank()) {
                resource.setChapterId(null);
            } else {
                Chapter chapter = chapterRepository.findById(request.chapterId())
                        .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + request.chapterId()));
                resource.setChapterId(chapter.getId());
            }
        }
        if (request.tags() != null) resource.setTags(new HashSet<>(request.tags()));

        resource = resourceRepository.saveAndFlush(resource);
        auditService.log(adminId, AuditAction.ADMIN_RESOURCE_UPDATED, "Resource", resource.getId(), ip,
                Map.of("title", resource.getTitle()));
        return toDto(resource, adminId);
    }

    @Transactional
    public void deleteResource(String adminId, String resourceId, String ip) {
        Resource resource = getEntityOrThrow(resourceId);
        String title = resource.getTitle();
        String url = resource.getUrl();
        String thumbnailUrl = resource.getThumbnailUrl();
        resourceRepository.delete(resource);
        auditService.log(adminId, AuditAction.ADMIN_RESOURCE_DELETED, "Resource", resourceId, ip, Map.of("title", title));
        // After the record is gone: a deleted resource's file must stop being reachable at its public URL.
        fileStorageService.deleteIfHosted(url);
        fileStorageService.deleteIfHosted(thumbnailUrl);
    }

    /** Replaces (or sets) the card image of a resource with an uploaded one. The previous hosted image is removed. */
    @Transactional
    public ResourceDto replaceThumbnail(String adminId, String resourceId, MultipartFile image, String ip) {
        Resource resource = getEntityOrThrow(resourceId);
        String previous = resource.getThumbnailUrl();
        resource.setThumbnailUrl(fileStorageService.storeImage(image, "resource-thumbnails"));
        resource = resourceRepository.saveAndFlush(resource);
        auditService.log(adminId, AuditAction.ADMIN_RESOURCE_UPDATED, "Resource", resource.getId(), ip,
                Map.of("title", resource.getTitle(), "thumbnail", "replaced"));
        fileStorageService.deleteIfHosted(previous);
        return toDto(resource, adminId);
    }

    /** Takes the uploaded card image off a resource; cards then fall back to a derived or drawn placeholder. */
    @Transactional
    public ResourceDto removeThumbnail(String adminId, String resourceId, String ip) {
        Resource resource = getEntityOrThrow(resourceId);
        String previous = resource.getThumbnailUrl();
        resource.setThumbnailUrl(null);
        resource = resourceRepository.saveAndFlush(resource);
        auditService.log(adminId, AuditAction.ADMIN_RESOURCE_UPDATED, "Resource", resource.getId(), ip,
                Map.of("title", resource.getTitle(), "thumbnail", "removed"));
        fileStorageService.deleteIfHosted(previous);
        return toDto(resource, adminId);
    }

    @Transactional
    public boolean toggleSave(String userId, String resourceId) {
        getEntityOrThrow(resourceId);
        var existing = resourceSaveRepository.findByResourceIdAndUserId(resourceId, userId);
        if (existing.isPresent()) {
            resourceSaveRepository.delete(existing.get());
            return false;
        }
        resourceSaveRepository.save(ResourceSave.builder().resourceId(resourceId).userId(userId).build());
        return true;
    }

    private static ResourceType parseType(String label) {
        try {
            return ResourceType.fromLabel(label);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown resource type: " + label);
        }
    }

    /** Blank means "no category". */
    private static ResourceCategory parseCategory(String slug) {
        if (slug == null || slug.isBlank()) return null;
        try {
            return ResourceCategory.fromSlug(slug.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown resource category: " + slug);
        }
    }

    /** Null or 0 means "no duration". */
    private static Integer normalizeDuration(Integer minutes) {
        if (minutes == null || minutes == 0) return null;
        if (minutes < 0 || minutes > 6000) throw new BadRequestException("Duration must be between 1 and 6000 minutes");
        return minutes;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * A resource's URL is either an internal path (starts with "/") or an external destination.
     * Non-technical uploaders routinely omit the scheme (e.g. "notion.so/doc"); left as-is, that
     * string is later rendered as {@code <a href="notion.so/doc">}, which browsers resolve
     * relative to the current page rather than as an external URL — normalizing here (instead of
     * only on the frontend) means the fix applies no matter what client reads this resource.
     */
    private String normalizeUrl(String rawUrl) {
        String trimmed = rawUrl.trim();
        if (trimmed.startsWith("/") || trimmed.matches("(?i)^https?://.*")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private static String extensionOf(String url) {
        String path = url;
        int cut = path.indexOf('?');
        if (cut >= 0) path = path.substring(0, cut);
        String last = path.substring(path.lastIndexOf('/') + 1);
        int dot = last.lastIndexOf('.');
        return dot < 0 ? "" : last.substring(dot + 1).toLowerCase();
    }

    /** The saved-as name for a download: the resource title (made filesystem-safe) plus the file's extension. */
    private static String downloadFileName(Resource resource) {
        String base = resource.getTitle() == null ? "" : resource.getTitle()
                .replaceAll("[^\\p{L}\\p{N} ._-]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (base.length() > 100) base = base.substring(0, 100).trim();
        if (base.isBlank()) base = "resource";
        String extension = extensionOf(resource.getUrl());
        return extension.isEmpty() ? base : base + "." + extension;
    }

    private ResourceDto toDto(Resource resource, String viewerId) {
        String chapterName = resource.getChapterId() == null ? null
                : chapterRepository.findById(resource.getChapterId()).map(Chapter::getName).orElse(null);
        boolean isSaved = viewerId != null && resourceSaveRepository.findByResourceIdAndUserId(resource.getId(), viewerId).isPresent();
        boolean hosted = fileStorageService.isHostedUrl(resource.getUrl());
        String fileName = hosted ? downloadFileName(resource) : null;
        boolean previewable = hosted && PREVIEWABLE_EXTENSIONS.contains(extensionOf(resource.getUrl()));
        return resourceMapper.toDto(resource, chapterName, isSaved, fileName, previewable);
    }
}
