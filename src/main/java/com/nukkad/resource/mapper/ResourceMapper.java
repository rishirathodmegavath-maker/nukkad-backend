package com.nukkad.resource.mapper;

import com.nukkad.resource.dto.ResourceDto;
import com.nukkad.resource.entity.Resource;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class ResourceMapper {

    public ResourceDto toDto(Resource resource, String chapterName, boolean isSaved, boolean canManage) {
        return new ResourceDto(
                resource.getId(),
                resource.getTitle(),
                resource.getDescription(),
                resource.getType().getLabel(),
                resource.getUrl(),
                resource.getUploaderUserId(),
                resource.getChapterId(),
                chapterName,
                new HashSet<>(resource.getTags()),
                isSaved,
                canManage,
                resource.getCreatedAt(),
                resource.getUpdatedAt()
        );
    }
}
