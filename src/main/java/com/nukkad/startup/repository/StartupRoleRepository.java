package com.nukkad.startup.repository;

import com.nukkad.startup.entity.StartupRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StartupRoleRepository extends JpaRepository<StartupRole, String> {
    List<StartupRole> findByStartupId(String startupId);
}
