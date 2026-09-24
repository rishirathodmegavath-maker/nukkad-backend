package com.nukkad.industry.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.industry.dto.IndustryDetailDto;
import com.nukkad.industry.dto.IndustryDto;
import com.nukkad.industry.service.IndustryService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/industries")
@SecurityRequirement(name = "bearerAuth")
public class IndustryController {

    private final IndustryService industryService;

    public IndustryController(IndustryService industryService) {
        this.industryService = industryService;
    }

    @GetMapping
    public ApiResponse<List<IndustryDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @RequestParam(required = false) String q) {
        String viewerId = principal == null ? null : principal.id();
        return ApiResponse.ok(industryService.listIndustries(viewerId, q));
    }

    @GetMapping("/{slug}")
    public ApiResponse<IndustryDetailDto> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable String slug) {
        String viewerId = principal == null ? null : principal.id();
        return ApiResponse.ok(industryService.getIndustry(slug, viewerId));
    }
}
