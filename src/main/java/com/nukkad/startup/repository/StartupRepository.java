package com.nukkad.startup.repository;

import com.nukkad.startup.entity.Startup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StartupRepository extends JpaRepository<Startup, String>, JpaSpecificationExecutor<Startup> {
    long countByChapterId(String chapterId);
}
