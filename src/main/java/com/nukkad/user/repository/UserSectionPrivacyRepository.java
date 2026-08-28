package com.nukkad.user.repository;

import com.nukkad.user.entity.ProfileSection;
import com.nukkad.user.entity.UserSectionPrivacy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSectionPrivacyRepository extends JpaRepository<UserSectionPrivacy, String> {
    List<UserSectionPrivacy> findByUserId(String userId);

    Optional<UserSectionPrivacy> findByUserIdAndSection(String userId, ProfileSection section);

    void deleteByUserIdAndSection(String userId, ProfileSection section);
}
