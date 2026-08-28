package com.nukkad.user.repository;

import com.nukkad.user.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, String> {
    List<UserAchievement> findByUser_IdOrderBySortOrderAsc(String userId);
}
