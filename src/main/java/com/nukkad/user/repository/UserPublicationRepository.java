package com.nukkad.user.repository;

import com.nukkad.user.entity.UserPublication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPublicationRepository extends JpaRepository<UserPublication, String> {
    List<UserPublication> findByUser_IdOrderBySortOrderAsc(String userId);
}
