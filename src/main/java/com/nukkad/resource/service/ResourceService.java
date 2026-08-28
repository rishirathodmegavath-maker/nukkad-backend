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
import com.nukkad.resource.repository.ResourceSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.Set;

@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceSaveRepository resourceSaveRepository;
    private final ChapterRepository chapterRepository;
    private final ResourceMapper resourceMapper;
    private final FileStorageService fileStorageService;

    public ResourceService(ResourceRepository resourceRepository,
                            ResourceSaveRepository resourceSaveRepository,
                            ChapterRepository chapterRepository,
                            ResourceMapper resourceMapper,
                            FileStorageService fileStorageService) {
        this.resourceRepository = resourceRepository;
        this.resourceSaveRepository = resourceSaveRepository;
        this.chapterRepository = chapterRepository;
        this.resourceMapper = resourceMapper;
        this.fileStorageService = fileStorageService;
    }

    public Resource getEntityOrThrow(String id) {
        return resourceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Resource not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ResourceDto> listResources(String q, String type, String chapterId, String viewerId, int page, int size) {
        Specification<Resource> spec = ResourceSpecifications.combine(
                ResourceSpecifications.search(q),
                ResourceSpecifications.type(type),
                ResourceSpecifications.chapterId(chapterId)
        );
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return resourceRepository.findAll(spec, pageable).map(r -> toDto(r, viewerId));
    }

    @Transactional(readOnly = true)
    public ResourceDto getResource(String id, String viewerId) {
        return toDto(getEntityOrThrow(id), viewerId);
    }

    @Transactional
    public ResourceDto createResource(String uploaderId, String title, String description, String typeLabel,
                                       String url, MultipartFile file, String chapterId, Set<String> tags, String publicBaseUrl) {
        ResourceType type;
        try {
            type = ResourceType.fromLabel(typeLabel);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown resource type: " + typeLabel);
        }

        boolean hasFile = file != null && !file.isEmpty();
        boolean hasUrl = url != null && !url.isBlank();
        if (hasFile == hasUrl) {
            throw new BadRequestException("Provide either a URL or a file to upload — not both, not neither");
        }

        String finalUrl;
        if (hasFile) {
            FileStorageService.StoredMedia media = fileStorageService.storeMedia(file, "resources");
            finalUrl = publicBaseUrl + "/uploads/" + media.path();
        } else {
            finalUrl = url.trim();
        }

        String resolvedChapterId = null;
        if (chapterId != null && !chapterId.isBlank()) {
            resolvedChapterId = chapterRepository.findById(chapterId)
                    .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + chapterId))
                    .getId();
        }

        Resource resource = Resource.builder()
                .title(title.trim())
                .description(description)
                .type(type)
                .url(finalUrl)
                .uploaderUserId(uploaderId)
                .chapterId(resolvedChapterId)
                .tags(tags == null ? new HashSet<>() : new HashSet<>(tags))
                .build();
        resource = resourceRepository.saveAndFlush(resource);

        return toDto(resource, uploaderId);
    }

    @Transactional
    public ResourceDto updateResource(String userId, String resourceId, UpdateResourceRequest request) {
        Resource resource = getEntityOrThrow(resourceId);
        requireUploader(userId, resource);

        if (request.title() != null) resource.setTitle(request.title());
        if (request.description() != null) resource.setDescription(request.description());
        if (request.type() != null) {
            try {
                resource.setType(ResourceType.fromLabel(request.type()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown resource type: " + request.type());
            }
        }
        if (request.url() != null) {
            if (request.url().isBlank()) throw new BadRequestException("URL cannot be blank");
            resource.setUrl(request.url().trim());
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

        return toDto(resourceRepository.saveAndFlush(resource), userId);
    }

    @Transactional
    public void deleteResource(String userId, String resourceId) {
        Resource resource = getEntityOrThrow(resourceId);
        requireUploader(userId, resource);
        resourceRepository.delete(resource);
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

    private void requireUploader(String userId, Resource resource) {
        if (!userId.equals(resource.getUploaderUserId())) {
            throw new ForbiddenException("Only the uploader of this resource can perform this action");
        }
    }

    private ResourceDto toDto(Resource resource, String viewerId) {
        String chapterName = resource.getChapterId() == null ? null
                : chapterRepository.findById(resource.getChapterId()).map(Chapter::getName).orElse(null);
        boolean isSaved = viewerId != null && resourceSaveRepository.findByResourceIdAndUserId(resource.getId(), viewerId).isPresent();
        boolean canManage = viewerId != null && viewerId.equals(resource.getUploaderUserId());
        return resourceMapper.toDto(resource, chapterName, isSaved, canManage);
    }
}
