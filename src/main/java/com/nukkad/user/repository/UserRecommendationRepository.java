package com.nukkad.user.repository;

import com.nukkad.user.entity.UserRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRecommendationRepository extends JpaRepository<UserRecommendation, String> {
    Optional<UserRecommendation> findBySubjectUserIdAndAuthorUserId(String subjectUserId, String authorUserId);

    List<UserRecommendation> findBySubjectUserIdAndStatusOrderByCreatedAtDesc(String subjectUserId, UserRecommendation.Status status);
}
