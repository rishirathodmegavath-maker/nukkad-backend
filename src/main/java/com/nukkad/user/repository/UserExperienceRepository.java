package com.nukkad.user.repository;

import com.nukkad.user.entity.UserExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserExperienceRepository extends JpaRepository<UserExperience, String> {
    List<UserExperience> findByUser_IdOrderBySortOrderAsc(String userId);
}
