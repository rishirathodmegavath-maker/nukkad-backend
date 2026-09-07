package com.nukkad.chapter.service;

import com.nukkad.chapter.dto.ChapterActivityDto;
import com.nukkad.chapter.dto.ChapterDto;
import com.nukkad.chapter.dto.CreateChapterRequest;
import com.nukkad.chapter.dto.UpdateChapterRequest;
import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.mapper.ChapterMapper;
import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.event.entity.Event;
import com.nukkad.event.repository.EventRepository;
import com.nukkad.idea.entity.Idea;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.opportunity.entity.Opportunity;
import com.nukkad.opportunity.repository.OpportunityRepository;
import com.nukkad.resource.entity.Resource;
import com.nukkad.resource.repository.ResourceRepository;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;
    private final IdeaRepository ideaRepository;
    private final StartupRepository startupRepository;
    private final OpportunityRepository opportunityRepository;
    private final EventRepository eventRepository;
    private final ResourceRepository resourceRepository;
    private final ChapterMapper chapterMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;

    public ChapterService(ChapterRepository chapterRepository,
                           UserRepository userRepository,
                           IdeaRepository ideaRepository,
                           StartupRepository startupRepository,
                           OpportunityRepository opportunityRepository,
                           EventRepository eventRepository,
                           ResourceRepository resourceRepository,
                           ChapterMapper chapterMapper,
                           UserMapper userMapper,
                           FileStorageService fileStorageService) {
        this.chapterRepository = chapterRepository;
        this.userRepository = userRepository;
        this.ideaRepository = ideaRepository;
        this.startupRepository = startupRepository;
        this.opportunityRepository = opportunityRepository;
        this.eventRepository = eventRepository;
        this.resourceRepository = resourceRepository;
        this.chapterMapper = chapterMapper;
        this.userMapper = userMapper;
        this.fileStorageService = fileStorageService;
    }

    public Chapter getEntityOrThrow(String id) {
        return chapterRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + id));
    }

    @Transactional(readOnly = true)
    public ChapterDto getChapter(String id) {
        return toDtoWithCounts(getEntityOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<ChapterDto> listChapters(String q, String presidentUserId, int page, int size) {
        Specification<Chapter> searchSpec = (root, query, cb) -> {
            if (q == null || q.isBlank()) return cb.conjunction();
            String like = "%" + q.trim().toLowerCase() + "%";
            return cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(cb.coalesce(root.get("city"), "")), like));
        };
        Specification<Chapter> spec = searchSpec;
        if (presidentUserId != null && !presidentUserId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("presidentUserId"), presidentUserId));
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return chapterRepository.findAll(spec, pageable).map(this::toDtoWithCounts);
    }

    @Transactional
    public ChapterDto createChapter(String userId, CreateChapterRequest request) {
        String name = request.name().trim();
        if (chapterRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A chapter with this name already exists");
        }

        Chapter chapter = Chapter.builder()
                .name(name)
                .city(request.city())
                .country(request.country())
                .description(request.description())
                .coverImageUrl(request.coverImageUrl())
                .presidentUserId(userId)
                .build();
        try {
            chapter = chapterRepository.saveAndFlush(chapter);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests both passed the existsByNameIgnoreCase check above before
            // either committed; the loser hits the DB-level uq_chapters_name constraint (added in
            // V49) as the final backstop — same idiom as UserController's follow-toggle race.
            throw new ConflictException("A chapter with this name already exists");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.getSecurityRoles().add(SecurityRole.CHAPTER_PRESIDENT);
        // The president is automatically a member of the chapter they just created — without this,
        // a brand-new chapter shows "No members joined yet" on its own Members tab while its
        // president is displayed right above it as a clear contradiction.
        user.setChapterId(chapter.getId());
        user.setChapterJoinedAt(Instant.now());
        userRepository.save(user);

        return toDtoWithCounts(chapter);
    }

    @Transactional
    public ChapterDto updateChapter(String userId, String id, UpdateChapterRequest request) {
        Chapter chapter = getEntityOrThrow(id);
        if (!userId.equals(chapter.getPresidentUserId())) {
            throw new ForbiddenException("Only this chapter's president can update it");
        }

        if (request.name() != null) chapter.setName(request.name());
        if (request.city() != null) chapter.setCity(request.city());
        if (request.country() != null) chapter.setCountry(request.country());
        if (request.description() != null) chapter.setDescription(request.description());
        if (request.coverImageUrl() != null) chapter.setCoverImageUrl(request.coverImageUrl());

        return toDtoWithCounts(chapterRepository.saveAndFlush(chapter));
    }

    @Transactional
    public ChapterDto updateCoverImage(String userId, String id, MultipartFile file) {
        Chapter chapter = getEntityOrThrow(id);
        if (!userId.equals(chapter.getPresidentUserId())) {
            throw new ForbiddenException("Only this chapter's president can update its cover photo");
        }
        chapter.setCoverImageUrl(fileStorageService.storeImage(file, "chapter-covers"));
        return toDtoWithCounts(chapterRepository.saveAndFlush(chapter));
    }

    @Transactional
    public ChapterDto removeCoverImage(String userId, String id) {
        Chapter chapter = getEntityOrThrow(id);
        if (!userId.equals(chapter.getPresidentUserId())) {
            throw new ForbiddenException("Only this chapter's president can remove its cover photo");
        }
        chapter.setCoverImageUrl(null);
        return toDtoWithCounts(chapterRepository.saveAndFlush(chapter));
    }

    @Transactional
    public UserDto joinChapter(String userId, String chapterId) {
        getEntityOrThrow(chapterId);
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setChapterId(chapterId);
        user.setChapterJoinedAt(Instant.now());
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto leaveChapter(String userId, String chapterId) {
        Chapter chapter = getEntityOrThrow(chapterId);
        // A chapter must always have a valid president: removeMember() already refuses to remove
        // the president as a member, but leaveChapter() is a separate code path a president could
        // otherwise call on themselves, leaving chapter.presidentUserId pointing at someone who is
        // no longer actually a member. There is no transfer-presidency flow yet, so the only way
        // out for a president today is deleting the chapter (only allowed once it's empty).
        if (userId.equals(chapter.getPresidentUserId())) {
            throw new ForbiddenException("The chapter president can't leave — delete the chapter instead if you no longer want to run it");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (chapterId.equals(user.getChapterId())) {
            user.setChapterId(null);
            user.setChapterJoinedAt(null);
            user = userRepository.save(user);
        }
        return userMapper.toDto(user);
    }

    @Transactional
    public UserDto addMember(String requesterId, String chapterId, String targetUserId) {
        Chapter chapter = getEntityOrThrow(chapterId);
        if (!requesterId.equals(chapter.getPresidentUserId())) {
            throw new ForbiddenException("Only this chapter's president can add members");
        }
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));
        user.setChapterId(chapterId);
        user.setChapterJoinedAt(Instant.now());
        return userMapper.toPublicDto(userRepository.save(user));
    }

    @Transactional
    public UserDto removeMember(String requesterId, String chapterId, String targetUserId) {
        Chapter chapter = getEntityOrThrow(chapterId);
        if (!requesterId.equals(chapter.getPresidentUserId())) {
            throw new ForbiddenException("Only this chapter's president can remove members");
        }
        if (targetUserId.equals(chapter.getPresidentUserId())) {
            throw new ForbiddenException("The chapter president cannot be removed as a member");
        }
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));
        if (chapterId.equals(user.getChapterId())) {
            user.setChapterId(null);
            user.setChapterJoinedAt(null);
            user = userRepository.save(user);
        }
        return userMapper.toPublicDto(user);
    }

    @Transactional
    public void deleteChapter(String userId, String id) {
        Chapter chapter = getEntityOrThrow(id);
        if (!userId.equals(chapter.getPresidentUserId())) {
            throw new ForbiddenException("Only this chapter's president can delete it");
        }

        // The president isn't necessarily counted as a member (chapterId is only set once they
        // explicitly join their own chapter), so they're excluded here and handled separately below.
        long memberCount = userRepository.countByChapterIdAndIdNot(id, userId);
        long ideaCount = ideaRepository.countByChapterId(id);
        long startupCount = startupRepository.countByChapterId(id);
        long opportunityCount = opportunityRepository.countByChapterId(id);
        long eventCount = eventRepository.countByChapterId(id);
        long resourceCount = resourceRepository.countByChapterId(id);
        if (memberCount > 0 || ideaCount > 0 || startupCount > 0 || opportunityCount > 0 || eventCount > 0 || resourceCount > 0) {
            throw new ConflictException(
                    "This chapter still has members or content attached — remove them before deleting the chapter");
        }

        // If the president had joined their own chapter, clear that reference first: users.chapter_id
        // has no ON DELETE CASCADE, so a dangling reference would fail the delete below.
        User president = userRepository.findById(userId).orElse(null);
        if (president != null && id.equals(president.getChapterId())) {
            president.setChapterId(null);
            president.setChapterJoinedAt(null);
            userRepository.save(president);
        }

        chapterRepository.delete(chapter);
    }

    /**
     * A lightweight, read-only "recent activity" feed for a chapter — merges the most recently
     * created ideas/startups/opportunities/events/resources already scoped to this chapter (each
     * via its own chapterId column) plus recent member-joins, sorted by recency. This is purely a
     * presentation layer over existing entities; no activity-log table backs it.
     */
    @Transactional(readOnly = true)
    public List<ChapterActivityDto> listRecentActivity(String chapterId, int limit) {
        getEntityOrThrow(chapterId);
        int fetchLimit = Math.min(Math.max(limit, 1), 50);
        Pageable recent = PageRequest.of(0, fetchLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Pageable recentJoins = PageRequest.of(0, fetchLimit, Sort.by(Sort.Direction.DESC, "chapterJoinedAt"));

        List<ChapterActivityDto> items = new ArrayList<>();
        for (Idea idea : ideaRepository.findByChapterId(chapterId, recent)) {
            items.add(new ChapterActivityDto("IDEA", idea.getId(), idea.getTitle(), idea.getCreatorId(), null, null, idea.getCreatedAt()));
        }
        for (Startup startup : startupRepository.findByChapterId(chapterId, recent)) {
            items.add(new ChapterActivityDto("STARTUP", startup.getId(), startup.getName(), null, null, null, startup.getCreatedAt()));
        }
        for (Opportunity opportunity : opportunityRepository.findByChapterId(chapterId, recent)) {
            items.add(new ChapterActivityDto("OPPORTUNITY", opportunity.getId(), opportunity.getTitle(),
                    opportunity.getPostedByUserId(), null, null, opportunity.getCreatedAt()));
        }
        for (Event event : eventRepository.findByChapterId(chapterId, recent)) {
            items.add(new ChapterActivityDto("EVENT", event.getId(), event.getTitle(), event.getOrganizerUserId(), null, null, event.getCreatedAt()));
        }
        for (Resource resource : resourceRepository.findByChapterId(chapterId, recent)) {
            items.add(new ChapterActivityDto("RESOURCE", resource.getId(), resource.getTitle(),
                    resource.getUploaderUserId(), null, null, resource.getCreatedAt()));
        }
        for (User member : userRepository.findByChapterIdAndChapterJoinedAtIsNotNull(chapterId, recentJoins)) {
            items.add(new ChapterActivityDto("MEMBER_JOINED", member.getId(), null, member.getId(), null, null, member.getChapterJoinedAt()));
        }

        List<ChapterActivityDto> trimmed = items.stream()
                .sorted(Comparator.comparing(ChapterActivityDto::occurredAt).reversed())
                .limit(fetchLimit)
                .toList();

        Set<String> actorIds = trimmed.stream().map(ChapterActivityDto::actorUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, User> actors = actorIds.isEmpty() ? Map.of()
                : userRepository.findAllById(actorIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        return trimmed.stream().map(item -> {
            User actor = actors.get(item.actorUserId());
            String actorName = actor != null ? actor.getName() : null;
            String actorAvatarUrl = actor != null ? actor.getAvatarUrl() : null;
            String title = item.title() != null ? item.title() : actorName;
            return new ChapterActivityDto(item.type(), item.entityId(), title, item.actorUserId(), actorName, actorAvatarUrl, item.occurredAt());
        }).toList();
    }

    private ChapterDto toDtoWithCounts(Chapter chapter) {
        long memberCount = userRepository.countByChapterId(chapter.getId());
        long ideaCount = ideaRepository.countByChapterId(chapter.getId());
        long startupCount = startupRepository.countByChapterId(chapter.getId());
        long opportunityCount = opportunityRepository.countByChapterId(chapter.getId());
        long eventCount = eventRepository.countByChapterId(chapter.getId());
        long resourceCount = resourceRepository.countByChapterId(chapter.getId());
        return chapterMapper.toDto(chapter, memberCount, ideaCount, startupCount, opportunityCount, eventCount, resourceCount);
    }
}
