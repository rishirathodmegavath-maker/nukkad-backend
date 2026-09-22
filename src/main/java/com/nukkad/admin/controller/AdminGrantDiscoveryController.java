package com.nukkad.admin.controller;

import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.grant.discovery.GrantDiscoveryRunDto;
import com.nukkad.grant.discovery.GrantDiscoveryRunRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only visibility into the scheduled AI grant-discovery pipeline (see
 *  com.nukkad.grant.discovery.GrantDiscoveryService) -- there is deliberately no "run now" action
 *  here; the pipeline is schedule-only by design. Every grant it publishes is otherwise just a
 *  normal row in GET /api/admin/grants (its sourceUrl/discoveryOrigin/lastVerifiedAt fields mark
 *  it as such) -- this endpoint is only for the run history itself. */
@RestController
@RequestMapping("/api/admin/grant-discovery")
@SecurityRequirement(name = "bearerAuth")
public class AdminGrantDiscoveryController {

    private final GrantDiscoveryRunRepository runRepository;

    public AdminGrantDiscoveryController(GrantDiscoveryRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @GetMapping("/runs")
    public ApiResponse<PageResponse<GrantDiscoveryRunDto>> listRuns(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, AdminPaging.clampSize(size), Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = runRepository.findAll(pageable).map(GrantDiscoveryRunDto::from);
        return ApiResponse.ok(PageResponse.from(result));
    }
}
