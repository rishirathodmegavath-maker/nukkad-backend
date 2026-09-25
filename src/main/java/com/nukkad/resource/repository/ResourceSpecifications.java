package com.nukkad.resource.repository;

import com.nukkad.common.validation.LikePatterns;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.resource.entity.Resource;
import com.nukkad.resource.entity.ResourceCategory;
import com.nukkad.resource.entity.ResourceType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ResourceSpecifications {

    private ResourceSpecifications() {}

    /** A search word that is exactly a resource type's name (singular or plural, case-insensitive) also
     *  matches every resource of that type — so searching "video" finds the Video resources even when
     *  none of their titles/descriptions happen to contain that word. An arbitrary word never becomes a
     *  filter this way: only an exact match (after trimming) against one of these keys qualifies. */
    private static final Map<String, ResourceType> TYPE_SEARCH_ALIASES = buildTypeSearchAliases();

    private static Map<String, ResourceType> buildTypeSearchAliases() {
        Map<String, ResourceType> aliases = new HashMap<>();
        for (ResourceType type : ResourceType.values()) {
            String singular = type.getLabel().toLowerCase(Locale.ROOT);
            aliases.put(singular, type);
            aliases.put(singular + "s", type);
        }
        return Map.copyOf(aliases);
    }

    @SafeVarargs
    public static Specification<Resource> combine(Specification<Resource>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    public static Specification<Resource> search(String q) {
        if (q == null || q.isBlank()) return null;
        String trimmed = q.trim();
        String like = LikePatterns.contains(trimmed);
        ResourceType typeAlias = TYPE_SEARCH_ALIASES.get(trimmed.toLowerCase(Locale.ROOT));
        return (root, query, cb) -> {
            Predicate textMatch = cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("provider"), "")), like)
            );
            return typeAlias == null ? textMatch : cb.or(textMatch, cb.equal(root.get("type"), typeAlias));
        };
    }

    public static Specification<Resource> type(String typeLabel) {
        if (typeLabel == null || typeLabel.isBlank()) return null;
        ResourceType type;
        try {
            type = ResourceType.fromLabel(typeLabel.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown resource type: " + typeLabel);
        }
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
