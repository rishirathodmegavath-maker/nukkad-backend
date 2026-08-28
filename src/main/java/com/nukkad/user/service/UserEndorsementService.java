package com.nukkad.user.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.dto.EndorsementSummaryDto;
import com.nukkad.user.entity.User;
import com.nukkad.user.entity.UserEndorsement;
import com.nukkad.user.repository.UserEndorsementRepository;
import com.nukkad.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Split out of {@link UserService} — an endorsement is a user-to-user interaction (like Connection/Follow),
 * not profile content, and {@code UserService} is already large.
 */
@Service
public class UserEndorsementService {

    private final UserEndorsementRepository endorsementRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public UserEndorsementService(UserEndorsementRepository endorsementRepository,
                                   UserRepository userRepository,
                                   NotificationService notificationService) {
        this.endorsementRepository = endorsementRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public record ToggleResult(boolean endorsed, long count) {}

    @Transactional
    public ToggleResult toggle(String endorserId, String endorsedUserId, String skill) {
        if (endorserId.equals(endorsedUserId)) throw new BadRequestException("Cannot endorse your own skill");
        User endorsedUser = userRepository.findById(endorsedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + endorsedUserId));
        if (!endorsedUser.getSkills().contains(skill)) {
            throw new BadRequestException("This person hasn't listed that skill");
        }

        boolean endorsed;
        if (endorsementRepository.existsByEndorsedUserIdAndEndorserUserIdAndSkill(endorsedUserId, endorserId, skill)) {
            endorsementRepository.deleteByEndorsedUserIdAndEndorserUserIdAndSkill(endorsedUserId, endorserId, skill);
            endorsed = false;
        } else {
            endorsementRepository.save(UserEndorsement.builder()
                    .endorsedUserId(endorsedUserId).endorserUserId(endorserId).skill(skill).build());
            endorsed = true;
            User endorser = userRepository.findById(endorserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + endorserId));
            notificationService.notify(endorsedUserId, NotificationType.endorsement,
                    "New skill endorsement", endorser.getName() + " endorsed you for " + skill, endorserId, endorserId);
        }

        long count = endorsementRepository.countByEndorsedUserIdAndSkill(endorsedUserId, skill);
        return new ToggleResult(endorsed, count);
    }

    @Transactional(readOnly = true)
    public List<EndorsementSummaryDto> summary(String profileUserId, String viewerId) {
        Set<String> viewerEndorsed = viewerId == null || viewerId.equals(profileUserId)
                ? Set.of()
                : new HashSet<>(endorsementRepository.findEndorsedSkills(profileUserId, viewerId));
        return endorsementRepository.countGroupedBySkill(profileUserId).stream()
                .map(row -> new EndorsementSummaryDto(row.getSkill(), (int) row.getCount(), viewerEndorsed.contains(row.getSkill())))
                .toList();
    }
}
