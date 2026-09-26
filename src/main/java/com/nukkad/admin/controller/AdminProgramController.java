package com.nukkad.admin.controller;

import com.nukkad.admin.dto.UpdateProgramSettingsRequest;
import com.nukkad.admin.service.AdminProgramSettingsService;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.program.dto.ProgramDto;
import com.nukkad.program.service.ProgramService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin's list/get is the same content the member-facing catalog serves (no separate admin
 *  view needed — there's nothing hidden in it), so only the settings edit lives here. */
@RestController
@RequestMapping("/api/admin/programs")
@SecurityRequirement(name = "bearerAuth")
public class AdminProgramController {

    private final AdminProgramSettingsService adminProgramSettingsService;
    private final ProgramService programService;

    public AdminProgramController(AdminProgramSettingsService adminProgramSettingsService, ProgramService programService) {
        this.adminProgramSettingsService = adminProgramSettingsService;
        this.programService = programService;
    }

    @GetMapping
    public ApiResponse<List<ProgramDto>> list() {
        return ApiResponse.ok(programService.list());
    }

    @PatchMapping("/{key}/settings")
    public ApiResponse<ProgramDto> updateSettings(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable String key,
                                                     @Valid @RequestBody UpdateProgramSettingsRequest request,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminProgramSettingsService.update(principal.id(), key, request, httpRequest.getRemoteAddr()));
    }
}
