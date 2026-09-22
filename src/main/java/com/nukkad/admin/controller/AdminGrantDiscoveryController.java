package com.nukkad.admin.controller;

import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.grant.discovery.GrantDiscoveryProperties;
import com.nukkad.grant.discovery.GrantDiscoveryRun;
import com.nukkad.grant.discovery.GrantDiscoveryRunDto;
import com.nukkad.grant.discovery.GrantDiscoveryRunRepository;
import com.nukkad.grant.discovery.GrantDiscoveryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Visibility into, and a manual trigger for, the AI grant-discovery pipeline (see
 *  com.nukkad.grant.discovery.GrantDiscoveryService). The pipeline's own automatic cadence is
 *  unchanged (GrantDiscoveryScheduler, nightly cron) -- /run just lets an admin fire one batch on
 *  demand, e.g. to verify the pipeline works right after turning it on, rather than waiting for
 *  the next scheduled run. Every grant it publishes is otherwise just a normal row in
 *  GET /api/admin/grants (its sourceUrl/discoveryOrigin/lastVerifiedAt fields mark it as such). */
@RestController
@RequestMapping("/api/admin/grant-discovery")
@SecurityRequirement(name = "bearerAuth")
public class AdminGrantDiscoveryController {

    private final GrantDiscoveryRunRepository runRepository;
    private final GrantDiscoveryService discoveryService;
    private final GrantDiscoveryProperties properties;

    public AdminGrantDiscoveryController(GrantDiscoveryRunRepository runRepository,
                                          GrantDiscoveryService discoveryService,
                                          GrantDiscoveryProperties properties) {
        this.runRepository = runRepository;
        this.discoveryService = discoveryService;
        this.properties = properties;
    }

    @GetMapping("/runs")
    public ApiResponse<PageResponse<GrantDiscoveryRunDto>> listRuns(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, AdminPaging.clampSize(size), Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = runRepository.findAll(pageable).map(GrantDiscoveryRunDto::from);
        return ApiResponse.ok(PageResponse.from(result));
    }

    /** Synchronous: a Gemini call plus a handful of DB writes, same call shape the scheduler
     *  already makes unattended every night -- no separate async/job infrastructure needed. Still
     *  gated on the master switch so flipping GRANT_DISCOVERY_ENABLED=false always fully stops the
     *  pipeline from touching Gemini or the grants table, manual trigger included. */
    @PostMapping("/run")
    public ApiResponse<GrantDiscoveryRunDto> runNow() {
        if (!properties.enabled()) {
            throw new BadRequestException("Grant discovery is disabled (GRANT_DISCOVERY_ENABLED=false)");
        }
        GrantDiscoveryRun result = discoveryService.runNextBatch();
        return ApiResponse.ok(GrantDiscoveryRunDto.from(result));
    }
}
