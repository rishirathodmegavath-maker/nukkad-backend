package com.nukkad.startup.repository;

import com.nukkad.startup.entity.StartupMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StartupMaterialRepository extends JpaRepository<StartupMaterial, String> {
    List<StartupMaterial> findByStartupIdOrderBySortOrderAscCreatedAtAsc(String startupId);
}
