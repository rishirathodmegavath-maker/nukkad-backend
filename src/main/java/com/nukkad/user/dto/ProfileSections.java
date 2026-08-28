package com.nukkad.user.dto;

import com.nukkad.user.entity.UserAchievement;
import com.nukkad.user.entity.UserCertification;
import com.nukkad.user.entity.UserEducation;
import com.nukkad.user.entity.UserExperience;
import com.nukkad.user.entity.UserProject;
import com.nukkad.user.entity.UserPublication;

import java.util.List;

/** Groups the viewer-agnostic profile sub-resource lists so {@code UserMapper.toDto} doesn't keep growing positional list params. */
public record ProfileSections(
        List<UserExperience> experiences,
        List<UserEducation> education,
        List<UserAchievement> achievements,
        List<UserProject> projects,
        List<UserCertification> certifications,
        List<UserPublication> publications
) {
    public static ProfileSections empty() {
        return new ProfileSections(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
