package com.nukkad.startup.repository;

import com.nukkad.startup.entity.StartupProfileView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StartupProfileViewRepository extends JpaRepository<StartupProfileView, String> {
    long countByStartupId(String startupId);
}
