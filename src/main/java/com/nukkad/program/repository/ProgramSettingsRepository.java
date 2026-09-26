package com.nukkad.program.repository;

import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramSettingsRepository extends JpaRepository<ProgramSettings, Program> {
}
