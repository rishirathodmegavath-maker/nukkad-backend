package com.nukkad.user.repository;

import com.nukkad.user.entity.UserEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserEducationRepository extends JpaRepository<UserEducation, String> {
    List<UserEducation> findByUser_IdOrderBySortOrderAsc(String userId);
}
