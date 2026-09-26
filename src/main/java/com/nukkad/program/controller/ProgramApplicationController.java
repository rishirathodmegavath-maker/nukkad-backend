package com.nukkad.program.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.program.dto.ProgramApplicationDto;
import com.nukkad.program.dto.SaveDraftRequest;
import com.nukkad.program.service.ProgramApplicationService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The applicant's own applications — "My Applications", starting/resuming a draft, submitting,
 *  withdrawing. Every method takes the caller's own id from the authenticated principal, never a
 *  path/body-supplied user id, so one applicant can never read or act on another's application. */
@RestController
@RequestMapping("/api/program-applications")
@SecurityRequirement(name = "bearerAuth")
public class ProgramApplicationController {

    private final ProgramApplicationService programApplicationService;

    public ProgramApplicationController(ProgramApplicationService programApplicationService) {
        this.programApplicationService = programApplicationService;
    }

    @GetMapping("/mine")
    public ApiResponse<List<ProgramApplicationDto>> getMine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(programApplicationService.getMine(principal.id()));
    }

    @GetMapping("/mine/{program}")
    public ApiResponse<ProgramApplicationDto> getMineForProgram(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                  @PathVariable String program) {
        return ApiResponse.ok(programApplicationService.getMineForProgram(principal.id(), program));
    }

    @PutMapping("/{program}/draft")
    public ApiResponse<ProgramApplicationDto> saveDraft(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable String program,
                                                          @RequestBody SaveDraftRequest request) {
        return ApiResponse.ok(programApplicationService.saveDraft(principal.id(), program, request.answers()));
    }

    @PostMapping("/{program}/submit")
    public ApiResponse<ProgramApplicationDto> submit(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @PathVariable String program) {
        return ApiResponse.ok(programApplicationService.submit(principal.id(), program));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<ProgramApplicationDto> withdraw(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @PathVariable String id) {
        return ApiResponse.ok(programApplicationService.withdraw(principal.id(), id));
    }
}
