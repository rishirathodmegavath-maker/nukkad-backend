package com.nukkad.user.repository;

import com.nukkad.user.entity.UserCertification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCertificationRepository extends JpaRepository<UserCertification, String> {
    List<UserCertification> findByUser_IdOrderBySortOrderAsc(String userId);
}
