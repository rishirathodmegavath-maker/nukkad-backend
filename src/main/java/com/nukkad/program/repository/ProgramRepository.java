package com.nukkad.program.repository;

import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramRepository extends JpaRepository<Program, String> {

    Optional<Program> findBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    List<Program> findByStatusOrderByDisplayOrderAscNameAsc(ProgramStatus status);

    List<Program> findAllByOrderByDisplayOrderAscNameAsc();
}
