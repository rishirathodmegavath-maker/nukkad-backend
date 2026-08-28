package com.nukkad.user.service;

import com.nukkad.user.dto.ProfileSections;
import com.nukkad.user.entity.User;
import org.springframework.stereotype.Component;

/**
 * Live-computed (not persisted) percentage of how filled-out a profile is, mirroring the
 * inline {@code isOnline} computation in {@link com.nukkad.user.mapper.UserMapper} — but this
 * needs the sub-entity lists too, so it's a dedicated component rather than a one-liner.
 */
@Component
public class ProfileCompletenessCalculator {

    public int compute(User user, ProfileSections sections) {
        int score = 0;
        if (isPresent(user.getAvatarUrl())) score += 10;
        if (isPresent(user.getCoverUrl())) score += 5;
        if (isPresent(user.getHeadline())) score += 10;
        if (user.getBio() != null && user.getBio().trim().length() >= 20) score += 10;
        if (isPresent(user.getLocation())) score += 5;
        if (user.getSkills().size() >= 3) score += 10;
        if (!sections.experiences().isEmpty() || !sections.education().isEmpty()) score += 15;
        if (!sections.projects().isEmpty()) score += 10;
        if (!user.getSocialLinks().isEmpty()) score += 10;
        if (!user.getLookingFor().isEmpty() || !user.getOpenTo().isEmpty()) score += 5;
        if (!sections.achievements().isEmpty() || !sections.certifications().isEmpty() || !sections.publications().isEmpty()) score += 10;
        return score;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
