package com.nukkad.startup.repository;

import com.nukkad.startup.entity.StartupUpdate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StartupUpdateRepository extends JpaRepository<StartupUpdate, String> {
    Page<StartupUpdate> findByStartupIdOrderByCreatedAtDesc(String startupId, Pageable pageable);
}
