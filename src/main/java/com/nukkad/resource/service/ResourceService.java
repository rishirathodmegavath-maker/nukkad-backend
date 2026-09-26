package com.nukkad.resource.service;

import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.paging.PageRequests;
import com.nukkad.common.publishing.PublisherIdentity;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    /** What an admin fills in to add a resource; exactly one of the url here or an uploaded file supplies its
     *  content. {@code publisherIdentity} picks which curator identity to credit (must name one of
     *  {@link PublisherIdentity}'s constants, case-insensitive, blank falls back to plain BuildAdda) — never
     *  the {@code provider} field. */
    public record NewResource(String title, String description, String type, String url, String category,
                              String provider, Integer durationMinutes, boolean featured, String chapterId,
                              Set<String> tags, String publisherIdentity) {}

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

    /** "All" is a legitimate public URL/UI concept (the unfiltered browse), not a real shelf — treat it the
     *  same as no category filter instead of rejecting it as an unknown slug. */
    private static String normalizeCategoryFilter(String category) {
        return "all".equalsIgnoreCase(category) ? null : category;
    }

    @Transactional(readOnly = true)
    public Page<ResourceDto> listResources(String q, String type, String category, Boolean featured, String chapterId,
                                            String viewerId, int page, int size) {
        String normalizedCategory = normalizeCategoryFilter(category);
        // createdAt only has second precision, so break ties on id — otherwise rows created together can repeat or vanish between pages.
        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        // The unfiltered "All" browse: newest-first alone buries every other type behind whichever one was
        // added most recently in bulk. A type or category filter already narrows to one kind of content
        // (or one shelf), so those keep the plain recency order unchanged.
        boolean unfiltered = (type == null || type.isBlank()) && (normalizedCategory == null || normalizedCategory.isBlank());
        if (unfiltered) {
            return listAllInterleavedByType(q, featured, chapterId, viewerId, pageable);
        }

        Specification<Resource> spec = ResourceSpecifications.combine(
                ResourceSpecifications.search(q),
                ResourceSpecifications.type(type),
                ResourceSpecifications.category(normalizedCategory),
                ResourceSpecifications.featured(featured),
                ResourceSpecifications.chapterId(chapterId)
        );
        return resourceRepository.findAll(spec, pageable).map(r -> toDto(r, viewerId));
    }

    /** The most the unfiltered "All" browse will pull into memory to interleave — a defensive ceiling this
     *  admin-curated library is not expected to reach; a match beyond it keeps its recency position instead
     *  of taking part in the interleave. */
    private static final int MAX_INTERLEAVE_FETCH = 2000;

    /**
     * Every matching resource, newest first within its own type, then taken one type at a time in turn
     * (round-robin) so a page makes it obvious multiple types exist instead of showing one type at a time.
     * Same shape as {@link #mix}'s shelf-and-type lanes, but over the whole matching set and sliced with
     * ordinary page/size instead of a capped one-shot front-page sample.
     */
    private Page<ResourceDto> listAllInterleavedByType(String q, Boolean featured, String chapterId,
                                                         String viewerId, Pageable pageable) {
        Specification<Resource> baseSpec = ResourceSpecifications.combine(
                ResourceSpecifications.search(q),
                ResourceSpecifications.featured(featured),
                ResourceSpecifications.chapterId(chapterId)
        );
        Sort newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        List<Resource> matches = resourceRepository.findAll(baseSpec, PageRequest.of(0, MAX_INTERLEAVE_FETCH, newestFirst)).getContent();

        List<Resource> interleaved = interleaveByType(matches);
        int total = interleaved.size();
        int from = Math.min(pageable.getPageNumber() * pageable.getPageSize(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        List<ResourceDto> content = interleaved.subList(from, to).stream().map(r -> toDto(r, viewerId)).toList();
        return new PageImpl<>(content, pageable, total);
    }

    /** Groups (already-newest-first) resources by type, then takes one from each group in turn. Fully
     *  deterministic — the grouping and the round order both come from the input's own order — so paging
     *  over the result never repeats or skips a resource. */
    private static List<Resource> interleaveByType(List<Resource> newestFirst) {
        Map<ResourceType, List<Resource>> byType = new LinkedHashMap<>();
        for (Resource resource : newestFirst) {
            byType.computeIfAbsent(resource.getType(), t -> new ArrayList<>()).add(resource);
        }
        List<List<Resource>> lanes = new ArrayList<>(byType.values());
        List<Resource> result = new ArrayList<>(newestFirst.size());
        for (int round = 0; result.size() < newestFirst.size(); round++) {
            boolean tookAny = false;
            for (List<Resource> lane : lanes) {
                if (round < lane.size()) {
                    result.add(lane.get(round));
                    tookAny = true;
                }
            }
            if (!tookAny) break;
        }
        return result;
    }

    /** The most a front-page mix can ask for. */
    private static final int MAX_MIX_SIZE = 24;

    /**
     * A varied selection for the library's front page. Just taking the newest resources fills the page with
     * whatever was uploaded last (a bulk import of essays buries everything else), so this takes one from every
     * shelf-and-type combination in turn (a video, an essay, a template, a tool, ...) and then goes round again.
     * Inside a combination the order is newest first, or featured first when {@code preferFeatured}.
     */
    @Transactional(readOnly = true)
    public List<ResourceDto> mix(int size, boolean preferFeatured, String viewerId) {
        int wanted = Math.max(1, Math.min(size, MAX_MIX_SIZE));
        // createdAt only has second precision, so break ties on id (same reason as listResources).
        Sort sort = preferFeatured
                ? Sort.by(Sort.Order.desc("featured"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        List<List<Resource>> lanes = new ArrayList<>();
        for (Object[] group : resourceRepository.findShelfAndTypeGroups()) {
            Page<Resource> best = resourceRepository.findAll(
                    ResourceSpecifications.shelfAndType((ResourceCategory) group[0], (ResourceType) group[1]),
                    PageRequest.of(0, wanted, sort));
            if (best.hasContent()) lanes.add(best.getContent());
        }

        // The combination whose best resource is most preferred (featured first if asked, then newest) leads every round.
        Comparator<Resource> mostPreferred = Comparator
                .comparing((Resource r) -> preferFeatured && r.isFeatured() ? 0 : 1)
                .thenComparing(Resource::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Resource::getId, Comparator.reverseOrder());
        lanes.sort((a, b) -> mostPreferred.compare(a.get(0), b.get(0)));

        List<Resource> picked = new ArrayList<>();
        for (int round = 0; picked.size() < wanted; round++) {
            boolean tookAny = false;
            for (List<Resource> lane : lanes) {
                if (round < lane.size() && picked.size() < wanted) {
                    picked.add(lane.get(round));
                    tookAny = true;
                }
            }
            if (!tookAny) break;
        }
        return picked.stream().map(r -> toDto(r, viewerId)).toList();
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
                .publisherIdentity(PublisherIdentity.parse(in.publisherIdentity(), PublisherIdentity.BUILDADDA))
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
        deleteOne(adminId, getEntityOrThrow(resourceId), ip);
    }

    /** Deletes every one of the given resources, or none of them. Every id must exist first — if any is
     *  missing, nothing is deleted and a {@link ResourceNotFoundException} is thrown; there's no partial
     *  bulk delete. One {@code ADMIN_RESOURCE_DELETED} audit-log row is still written per resource, exactly
     *  like the single-item {@link #deleteResource}. */
    @Transactional
    public void bulkDeleteResources(String adminId, List<String> ids, String ip) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("No resources specified");
        }
        // Same ceiling PageRequests already uses for "how many rows is one reasonable request" --
        // nothing an admin selects by hand on one page could ever reach it; this only stops a
        // crafted request from asking to delete an unbounded number of rows in one call.
        if (ids.size() > PageRequests.MAX_SIZE) {
            throw new BadRequestException("Cannot delete more than " + PageRequests.MAX_SIZE + " resources at once");
        }
        Set<String> uniqueIds = new LinkedHashSet<>(ids);
        List<Resource> resources = resourceRepository.findAllById(uniqueIds);
        if (resources.size() != uniqueIds.size()) {
            throw new ResourceNotFoundException("One or more resources were not found");
        }
        for (Resource resource : resources) {
            deleteOne(adminId, resource, ip);
        }
    }

    private void deleteOne(String adminId, Resource resource, String ip) {
        String resourceId = resource.getId();
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
