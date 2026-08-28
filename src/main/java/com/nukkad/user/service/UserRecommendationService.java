package com.nukkad.user.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.dto.RecommendationDto;
import com.nukkad.user.dto.WriteRecommendationRequest;
import com.nukkad.user.entity.User;
import com.nukkad.user.entity.UserRecommendation;
import com.nukkad.user.repository.UserRecommendationRepository;
import com.nukkad.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Split out of {@link UserService}, same rationale as {@link UserEndorsementService} — this is a
 * user-to-user interaction with an approval workflow, not profile content the subject owns directly.
 */
@Service
public class UserRecommendationService {

    private final UserRecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public UserRecommendationService(UserRecommendationRepository recommendationRepository,
                                      UserRepository userRepository,
                                      NotificationService notificationService) {
        this.recommendationRepository = recommendationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public RecommendationDto write(String authorId, String subjectId, WriteRecommendationRequest request) {
        if (authorId.equals(subjectId)) throw new BadRequestException("Cannot write a recommendation for yourself");
        userRepository.findById(subjectId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + subjectId));
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authorId));

        UserRecommendation rec = recommendationRepository.findBySubjectUserIdAndAuthorUserId(subjectId, authorId).orElse(null);
        if (rec != null && rec.getStatus() == UserRecommendation.Status.APPROVED) {
            throw new ConflictException("You already have an approved recommendation for this person — withdraw it first");
        }
        if (rec == null) {
            rec = UserRecommendation.builder().subjectUserId(subjectId).authorUserId(authorId).build();
        }
        rec.setRelationship(request.relationship());
        rec.setBody(request.body());
        rec.setStatus(UserRecommendation.Status.PENDING);
        rec.setRespondedAt(null);
        rec = recommendationRepository.save(rec);

        notificationService.notify(subjectId, NotificationType.recommendation,
                "New recommendation", author.getName() + " wrote you a recommendation", authorId, authorId);

        return toDto(rec, author);
    }

    @Transactional(readOnly = true)
    public List<RecommendationDto> listPublic(String subjectId) {
        return toDtos(recommendationRepository.findBySubjectUserIdAndStatusOrderByCreatedAtDesc(subjectId, UserRecommendation.Status.APPROVED));
    }

    @Transactional(readOnly = true)
    public List<RecommendationDto> listPendingForSubject(String subjectId) {
        return toDtos(recommendationRepository.findBySubjectUserIdAndStatusOrderByCreatedAtDesc(subjectId, UserRecommendation.Status.PENDING));
    }

    @Transactional
    public RecommendationDto approve(String subjectId, String id) {
        UserRecommendation rec = getOwnedBySubject(subjectId, id);
        rec.setStatus(UserRecommendation.Status.APPROVED);
        rec.setRespondedAt(Instant.now());
        rec = recommendationRepository.save(rec);
        return toDto(rec, userRepository.findById(rec.getAuthorUserId()).orElse(null));
    }

    @Transactional
    public RecommendationDto reject(String subjectId, String id) {
        UserRecommendation rec = getOwnedBySubject(subjectId, id);
        rec.setStatus(UserRecommendation.Status.REJECTED);
        rec.setRespondedAt(Instant.now());
        rec = recommendationRepository.save(rec);
        return toDto(rec, userRepository.findById(rec.getAuthorUserId()).orElse(null));
    }

    @Transactional
    public void deleteAuthored(String authorId, String id) {
        UserRecommendation rec = recommendationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found: " + id));
        if (!rec.getAuthorUserId().equals(authorId)) throw new ForbiddenException("You can only delete your own recommendations");
        recommendationRepository.delete(rec);
    }

    private UserRecommendation getOwnedBySubject(String subjectId, String id) {
        UserRecommendation rec = recommendationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found: " + id));
        if (!rec.getSubjectUserId().equals(subjectId)) throw new ForbiddenException("You can only moderate recommendations about you");
        return rec;
    }

    private List<RecommendationDto> toDtos(List<UserRecommendation> recs) {
        if (recs.isEmpty()) return List.of();
        Set<String> authorIds = recs.stream().map(UserRecommendation::getAuthorUserId).collect(Collectors.toSet());
        Map<String, User> authors = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return recs.stream().map(r -> toDto(r, authors.get(r.getAuthorUserId()))).toList();
    }

    private RecommendationDto toDto(UserRecommendation r, User author) {
        return new RecommendationDto(r.getId(), r.getAuthorUserId(),
                author != null ? author.getName() : "Unknown",
                author != null ? author.getAvatarUrl() : null,
                author != null ? author.getHeadline() : null,
                r.getRelationship(), r.getBody(), r.getStatus().name(), r.getCreatedAt(), r.getRespondedAt());
    }
}
