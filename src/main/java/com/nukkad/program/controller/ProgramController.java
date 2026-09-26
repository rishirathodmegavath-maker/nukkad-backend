package com.nukkad.program.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.program.dto.ProgramDto;
import com.nukkad.program.service.ProgramService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Program discovery — the landing page's card list and each program's detail page. */
@RestController
@RequestMapping("/api/programs")
@SecurityRequirement(name = "bearerAuth")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @GetMapping
    public ApiResponse<List<ProgramDto>> list() {
        return ApiResponse.ok(programService.list());
    }

    @GetMapping("/{key}")
    public ApiResponse<ProgramDto> get(@PathVariable String key) {
        return ApiResponse.ok(programService.get(key));
    }
}
