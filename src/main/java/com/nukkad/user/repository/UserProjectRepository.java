package com.nukkad.user.repository;

import com.nukkad.user.entity.UserProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserProjectRepository extends JpaRepository<UserProject, String> {
    List<UserProject> findByUser_IdOrderBySortOrderAsc(String userId);
}
