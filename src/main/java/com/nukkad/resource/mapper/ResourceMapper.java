package com.nukkad.resource.mapper;

import com.nukkad.resource.dto.ResourceDto;
import com.nukkad.resource.entity.Resource;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class ResourceMapper {

    public ResourceDto toDto(Resource resource, String chapterName, boolean isSaved, String fileName, boolean previewable) {
        return new ResourceDto(
                resource.getId(),
                resource.getTitle(),
                resource.getDescription(),
                resource.getType().getLabel(),
                resource.getCategory() == null ? null : resource.getCategory().getSlug(),
                resource.getProvider(),
                resource.getThumbnailUrl(),
                resource.getDurationMinutes(),
                resource.isFeatured(),
                resource.getUrl(),
                resource.getUploaderUserId(),
                resource.getPublisherIdentity().name(),
                resource.getChapterId(),
                chapterName,
                new HashSet<>(resource.getTags()),
                isSaved,
                fileName,
                previewable,
                resource.getCreatedAt(),
                resource.getUpdatedAt()
        );
    }
}
