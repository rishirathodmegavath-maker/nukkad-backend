package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminInvestorDto;
import com.nukkad.admin.dto.UpdateInvestorRequest;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.investor.dto.InvestorImportBatchDto;
import com.nukkad.investor.dto.InvestorImportIssueDto;
import com.nukkad.investor.dto.InvestorImportPreviewDto;
import com.nukkad.investor.dto.InvestorIntroRequestDto;
import com.nukkad.investor.service.InvestorCatalogService;
import com.nukkad.investor.service.InvestorImportService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * The only place investor catalog records are added, edited or retired. Mirrors AdminResourceController: everything
 * under /api/admin/** already requires an admin role AND an admin-portal token (see SecurityConfig), so no member —
 * even one whose account is an admin — can ever reach these. No investor business logic lives here, it's all in
 * InvestorCatalogService (one-at-a-time CRUD) and InvestorImportService (bulk CSV import).
 */
@RestController
@RequestMapping("/api/admin/investor-catalog")
@SecurityRequirement(name = "bearerAuth")
public class AdminInvestorCatalogController {

    private final InvestorCatalogService investorCatalogService;
    private final InvestorImportService investorImportService;

    public AdminInvestorCatalogController(InvestorCatalogService investorCatalogService, InvestorImportService investorImportService) {
        this.investorCatalogService = investorCatalogService;
        this.investorImportService = investorImportService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminInvestorDto>> list(@RequestParam(required = false) String q,
                                                               @RequestParam(required = false) String type,
                                                               @RequestParam(required = false) Boolean active,
                                                               @RequestParam(required = false) Boolean visible,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(investorCatalogService.listForAdmin(q, type, active, visible, page, AdminPaging.clampSize(size))));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminInvestorDto> get(@PathVariable String id) {
        return ApiResponse.ok(investorCatalogService.getForAdmin(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminInvestorDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @RequestParam String name,
                                                  @RequestParam String investorType,
                                                  @RequestParam(required = false) String description,
                                                  @RequestParam(required = false) String location,
                                                  @RequestParam(required = false) String country,
                                                  @RequestParam(required = false) String website,
                                                  @RequestParam(required = false) String domain,
                                                  @RequestParam(required = false) Set<String> sectors,
                                                  @RequestParam(required = false) Set<String> stages,
                                                  @RequestParam(required = false) Set<String> programs,
                                                  @RequestParam(required = false) Set<String> keyPeople,
                                                  @RequestParam(required = false) Integer investmentCount,
                                                  @RequestParam(required = false) Integer exitCount,
                                                  @RequestParam(required = false) Long chequeMin,
                                                  @RequestParam(required = false) Long chequeMax,
                                                  @RequestParam(defaultValue = "true") boolean active,
                                                  @RequestParam(defaultValue = "true") boolean visible,
                                                  @RequestParam(required = false) String facebookUrl,
                                                  @RequestParam(required = false) String instagramUrl,
                                                  @RequestParam(required = false) String linkedinUrl,
                                                  @RequestParam(required = false) String twitterUrl,
                                                  @RequestParam(required = false) String contactEmail,
                                                  @RequestParam(required = false) Boolean contactEmailVerified,
                                                  @RequestParam(required = false) String secondaryEmail,
                                                  @RequestParam(required = false) String phoneNumber,
                                                  @RequestParam(required = false) String linkedInvestorProfileId,
                                                  @RequestParam(required = false) MultipartFile logo,
                                                  HttpServletRequest httpRequest) {
        var input = new InvestorCatalogService.NewInvestor(name, investorType, description, location, website,
                sectors, stages, chequeMin, chequeMax, active, visible, linkedInvestorProfileId,
                country, domain, programs, keyPeople, investmentCount, exitCount,
                facebookUrl, instagramUrl, linkedinUrl, twitterUrl, contactEmail, contactEmailVerified, secondaryEmail, phoneNumber);
        return ApiResponse.ok(investorCatalogService.create(principal.id(), input, logo, httpRequest.getRemoteAddr()));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminInvestorDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable String id,
                                                  @Valid @RequestBody UpdateInvestorRequest request,
                                                  HttpServletRequest httpRequest) {
        return ApiResponse.ok(investorCatalogService.update(principal.id(), id, request, httpRequest.getRemoteAddr()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                     @PathVariable String id,
                                     HttpServletRequest httpRequest) {
        investorCatalogService.delete(principal.id(), id, httpRequest.getRemoteAddr());
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminInvestorDto> replaceLogo(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable String id,
                                                        @RequestParam MultipartFile logo,
                                                        HttpServletRequest httpRequest) {
        return ApiResponse.ok(investorCatalogService.replaceLogo(principal.id(), id, logo, httpRequest.getRemoteAddr()));
    }

    @DeleteMapping("/{id}/logo")
    public ApiResponse<AdminInvestorDto> removeLogo(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @PathVariable String id,
                                                       HttpServletRequest httpRequest) {
        return ApiResponse.ok(investorCatalogService.removeLogo(principal.id(), id, httpRequest.getRemoteAddr()));
    }

    @GetMapping("/introductions")
    public ApiResponse<PageResponse<InvestorIntroRequestDto>> introductions(@RequestParam(required = false) String status,
                                                                               @RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(investorCatalogService.listIntroRequestsForAdmin(status, page, AdminPaging.clampSize(size))));
    }

    @PatchMapping("/introductions/{id}/close")
    public ApiResponse<InvestorIntroRequestDto> closeIntroduction(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                     @PathVariable String id,
                                                                     HttpServletRequest httpRequest) {
        return ApiResponse.ok(investorCatalogService.closeIntroRequest(principal.id(), id, httpRequest.getRemoteAddr()));
    }

    // ---- Bulk CSV import — see InvestorImportService ----

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<InvestorImportPreviewDto> previewImport(@RequestParam MultipartFile file) {
        return ApiResponse.ok(investorImportService.preview(file));
    }

    /** Kicks off processing off-request and returns immediately (status PENDING/PROCESSING) — the caller
     *  polls GET .../import/{id} for progress and the final report. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<InvestorImportBatchDto> startImport(@AuthenticationPrincipal AuthenticatedUser principal,
                                                              @RequestParam MultipartFile file,
                                                              HttpServletRequest httpRequest) {
        return ApiResponse.ok(investorImportService.startImport(principal.id(), file, httpRequest.getRemoteAddr()));
    }

    @GetMapping("/import")
    public ApiResponse<PageResponse<InvestorImportBatchDto>> listImports(@RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(investorImportService.listBatches(page, AdminPaging.clampSize(size))));
    }

    @GetMapping("/import/{id}")
    public ApiResponse<InvestorImportBatchDto> getImport(@PathVariable String id) {
        return ApiResponse.ok(investorImportService.getBatch(id));
    }

    @GetMapping("/import/{id}/issues")
    public ApiResponse<PageResponse<InvestorImportIssueDto>> getImportIssues(@PathVariable String id,
                                                                                 @RequestParam(defaultValue = "0") int page,
                                                                                 @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.from(investorImportService.listIssues(id, page, AdminPaging.clampSize(size))));
    }
}
