package com.nukkad.chapter.service;

import com.nukkad.chapter.dto.ChapterDto;
import com.nukkad.chapter.dto.CreateChapterRequest;
import com.nukkad.chapter.dto.UpdateChapterRequest;
import com.nukkad.chapter.entity.Chapter;
import com.nukkad.chapter.mapper.ChapterMapper;
import com.nukkad.chapter.repository.ChapterRepository;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.event.repository.EventRepository;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.resource.repository.ResourceRepository;
import com.nukkad.opportunity.repository.OpportunityRepository;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
        Chapter chapter = Chapter.builder()
                .name(request.name().trim())
                .city(request.city())
                .country(request.country())
                .description(request.description())
                .coverImageUrl(request.coverImageUrl())
                .presidentUserId(userId)
                .build();
        chapter = chapterRepository.saveAndFlush(chapter);

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.getSecurityRoles().add(SecurityRole.CHAPTER_PRESIDENT);
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
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto leaveChapter(String userId, String chapterId) {
        getEntityOrThrow(chapterId);
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (chapterId.equals(user.getChapterId())) {
            user.setChapterId(null);
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
        return userMapper.toDto(userRepository.save(user));
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
            user = userRepository.save(user);
        }
        return userMapper.toDto(user);
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
