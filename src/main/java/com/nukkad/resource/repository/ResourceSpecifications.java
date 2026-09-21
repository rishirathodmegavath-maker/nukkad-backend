package com.nukkad.resource.repository;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.resource.entity.Resource;
import com.nukkad.resource.entity.ResourceCategory;
import com.nukkad.resource.entity.ResourceType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Objects;

public final class ResourceSpecifications {

    private ResourceSpecifications() {}

    @SafeVarargs
    public static Specification<Resource> combine(Specification<Resource>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<Resource> search(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("provider"), "")), like)
        );
    }

    public static Specification<Resource> type(String typeLabel) {
        if (typeLabel == null || typeLabel.isBlank()) return null;
        ResourceType type = ResourceType.fromLabel(typeLabel.trim());
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Resource> chapterId(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("chapterId"), chapterId);
    }

    public static Specification<Resource> category(String slug) {
        if (slug == null || slug.isBlank()) return null;
        ResourceCategory category;
        try {
            category = ResourceCategory.fromSlug(slug.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown resource category: " + slug);
        }
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    /** Only the front-page shelf when true; null (or false) means no restriction. */
    public static Specification<Resource> featured(Boolean featured) {
        if (featured == null || !featured) return null;
        return (root, query, cb) -> cb.isTrue(root.get("featured"));
    }

    /** One shelf-and-type combination; a null category matches the resources that sit on no shelf. */
    public static Specification<Resource> shelfAndType(ResourceCategory category, ResourceType type) {
        return (root, query, cb) -> cb.and(
                category == null ? cb.isNull(root.get("category")) : cb.equal(root.get("category"), category),
                cb.equal(root.get("type"), type));
    }
}
