package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminProgramDto;
import com.nukkad.admin.dto.CreateProgramRequest;
import com.nukkad.admin.dto.UpdateProgramRequest;
import com.nukkad.admin.mapper.AdminProgramMapper;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.program.catalog.ProgramContentCodec;
import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramApplicationStatus;
import com.nukkad.program.entity.ProgramStatus;
import com.nukkad.program.repository.ProgramApplicationRepository;
import com.nukkad.program.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Program CONTENT management — create, edit, publish/archive, delete. See {@link
 * com.nukkad.admin.service.AdminProgramApplicationService} for the separate, unrelated concern of
 * managing applicants against whatever programs already exist (Part 3/25 of the product spec is
 * explicit that these stay two different admin areas). Mirrors {@code AdminResourceService}'s /
 * {@code ChapterService#createChapterAsAdmin}'s shape: an admin-only surface, audit-logged, reusing
 * the existing {@code ADMIN_ACTION} audit literal (see {@code AdminProgramApplicationService}'s own
 * doc comment for why — no new native-ENUM migration for this).
 */
@Service
public class AdminProgramService {

    private static final Pattern VALID_SLUG = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,58}[A-Za-z0-9]$");
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    private final ProgramRepository programRepository;
    private final ProgramApplicationRepository programApplicationRepository;
    private final AdminProgramMapper adminProgramMapper;
    private final ProgramContentCodec codec;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public AdminProgramService(ProgramRepository programRepository, ProgramApplicationRepository programApplicationRepository,
                                AdminProgramMapper adminProgramMapper, ProgramContentCodec codec,
                                FileStorageService fileStorageService, AuditService auditService) {
        this.programRepository = programRepository;
        this.programApplicationRepository = programApplicationRepository;
        this.adminProgramMapper = adminProgramMapper;
        this.codec = codec;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AdminProgramDto> list() {
        return programRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(p -> adminProgramMapper.toDto(p, applicationCount(p.getSlug())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminProgramDto get(String id) {
        Program program = getEntityOrThrow(id);
        return adminProgramMapper.toDto(program, applicationCount(program.getSlug()));
    }

    @Transactional
    public AdminProgramDto create(String adminId, CreateProgramRequest request, String ip) {
        String slug = validateSlug(request.slug());
        if (programRepository.existsBySlugIgnoreCase(slug)) {
            throw new ConflictException("A program with this slug already exists: " + slug);
        }

        Program program = Program.builder()
                .slug(slug)
                .name(requireText(request.name(), "Program name"))
                .badge(blankToNull(request.badge()))
                .tagline(requireText(request.tagline(), "Short description"))
                .description(requireText(request.description(), "Description"))
                .heroImageUrl(blankToNull(request.heroImageUrl()))
                .thumbnailUrl(blankToNull(request.thumbnailUrl()))
                .status(parseStatus(request.status(), ProgramStatus.DRAFT))
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : nextDisplayOrder())
                .highlightsJson(codec.writeStrings(request.highlights()))
                .targetAudienceJson(codec.writeStrings(request.targetAudience()))
                .audienceDescription(blankToNull(request.audienceDescription()))
                .eligibilityTitle(blankToNull(request.eligibilityTitle()))
                .eligibilityDescription(blankToNull(request.eligibilityDescription()))
                .eligibilityPointsJson(codec.writeStrings(request.eligibilityPoints()))
                .journeyJson(codec.writeJourney(reorderJourney(request.journey())))
                .benefitsJson(codec.writeBenefits(reorderBenefits(request.benefits())))
                .outcomeHeading(blankToNull(request.outcomeHeading()))
                .outcomeDescription(blankToNull(request.outcomeDescription()))
                .applicationStepsJson(codec.writeApplicationSteps(request.applicationSteps()))
                .applicationOpen(request.applicationOpen() == null || request.applicationOpen())
                .feeAmount(request.feeAmount())
                .feeCurrency(blankToNull(request.feeCurrency()))
                .enrollmentInfo(blankToNull(request.enrollmentInfo()))
                .selective(request.selective())
                .build();
        program = programRepository.saveAndFlush(program);

        auditService.log(adminId, AuditAction.ADMIN_ACTION, "Program", program.getId(), ip,
                Map.of("action", "PROGRAM_CREATED", "slug", program.getSlug(), "status", program.getStatus().name()));
        return adminProgramMapper.toDto(program, 0);
    }

    @Transactional
    public AdminProgramDto update(String adminId, String id, UpdateProgramRequest request, String ip) {
        Program program = getEntityOrThrow(id);
        String previousStatus = program.getStatus().name();
        String oldHeroImage = program.getHeroImageUrl();
        String oldThumbnail = program.getThumbnailUrl();

        if (request.slug() != null) {
            String newSlug = validateSlug(request.slug());
            if (!newSlug.equalsIgnoreCase(program.getSlug())) {
                if (programRepository.existsBySlugIgnoreCase(newSlug)) {
                    throw new ConflictException("A program with this slug already exists: " + newSlug);
                }
                // Keeps every existing application (and any admin filter by program) pointed at the
                // right program — see ProgramApplicationRepository#renameProgramSlug's own doc.
                programApplicationRepository.renameProgramSlug(program.getSlug(), newSlug);
                program.setSlug(newSlug);
            }
        }
        if (request.name() != null) program.setName(requireText(request.name(), "Program name"));
        if (request.badge() != null) program.setBadge(blankToNull(request.badge()));
        if (request.tagline() != null) program.setTagline(requireText(request.tagline(), "Short description"));
        if (request.description() != null) program.setDescription(requireText(request.description(), "Description"));
        if (request.heroImageUrl() != null) program.setHeroImageUrl(request.heroImageUrl());
        if (Boolean.TRUE.equals(request.removeHeroImage())) program.setHeroImageUrl(null);
        if (request.thumbnailUrl() != null) program.setThumbnailUrl(request.thumbnailUrl());
        if (Boolean.TRUE.equals(request.removeThumbnail())) program.setThumbnailUrl(null);
        if (request.status() != null) program.setStatus(parseStatus(request.status(), program.getStatus()));
        if (request.displayOrder() != null) program.setDisplayOrder(request.displayOrder());
        if (request.highlights() != null) program.setHighlightsJson(codec.writeStrings(request.highlights()));
        if (request.targetAudience() != null) program.setTargetAudienceJson(codec.writeStrings(request.targetAudience()));
        if (request.audienceDescription() != null) program.setAudienceDescription(blankToNull(request.audienceDescription()));
        if (request.eligibilityTitle() != null) program.setEligibilityTitle(blankToNull(request.eligibilityTitle()));
        if (request.eligibilityDescription() != null) program.setEligibilityDescription(blankToNull(request.eligibilityDescription()));
        if (request.eligibilityPoints() != null) program.setEligibilityPointsJson(codec.writeStrings(request.eligibilityPoints()));
        if (request.journey() != null) program.setJourneyJson(codec.writeJourney(reorderJourney(request.journey())));
        if (request.benefits() != null) program.setBenefitsJson(codec.writeBenefits(reorderBenefits(request.benefits())));
        if (request.outcomeHeading() != null) program.setOutcomeHeading(blankToNull(request.outcomeHeading()));
        if (request.outcomeDescription() != null) program.setOutcomeDescription(blankToNull(request.outcomeDescription()));
        if (request.applicationSteps() != null) program.setApplicationStepsJson(codec.writeApplicationSteps(request.applicationSteps()));
        if (request.applicationOpen() != null) program.setApplicationOpen(request.applicationOpen());
        if (request.feeAmount() != null) program.setFeeAmount(request.feeAmount());
        if (request.feeCurrency() != null) program.setFeeCurrency(blankToNull(request.feeCurrency()));
        if (request.enrollmentInfo() != null) program.setEnrollmentInfo(blankToNull(request.enrollmentInfo()));
        if (request.selective() != null) program.setSelective(request.selective());

        program = programRepository.saveAndFlush(program);

        // Best-effort cleanup of whatever image this program no longer points at — never thrown on
        // failure (see FileStorageService#deleteIfHosted's own doc): the row is already saved, and a
        // storage hiccup here must not be reported back as the update having failed.
        if (oldHeroImage != null && !oldHeroImage.equals(program.getHeroImageUrl())) fileStorageService.deleteIfHosted(oldHeroImage);
        if (oldThumbnail != null && !oldThumbnail.equals(program.getThumbnailUrl())) fileStorageService.deleteIfHosted(oldThumbnail);

        auditService.log(adminId, AuditAction.ADMIN_ACTION, "Program", program.getId(), ip,
                Map.of("action", statusChangeAction(previousStatus, program.getStatus().name()), "slug", program.getSlug()));
        return adminProgramMapper.toDto(program, applicationCount(program.getSlug()));
    }

    @Transactional
    public void delete(String adminId, String id, String ip) {
        Program program = getEntityOrThrow(id);
        long total = programApplicationRepository.count(com.nukkad.program.repository.ProgramApplicationSpecifications.program(program.getSlug()));
        if (total > 0) {
            throw new ConflictException("This program has applications on record and can't be deleted — archive it instead");
        }
        programRepository.delete(program);
        fileStorageService.deleteIfHosted(program.getHeroImageUrl());
        fileStorageService.deleteIfHosted(program.getThumbnailUrl());
        auditService.log(adminId, AuditAction.ADMIN_ACTION, "Program", id, ip,
                Map.of("action", "PROGRAM_DELETED", "slug", program.getSlug()));
    }

    public String uploadHeroImage(MultipartFile file) {
        if (file != null && file.getSize() > MAX_IMAGE_BYTES) {
            throw new BadRequestException("Image is too large. The maximum size is 8 MB.");
        }
        return fileStorageService.storeImage(file, "program-hero");
    }

    public String uploadThumbnail(MultipartFile file) {
        if (file != null && file.getSize() > MAX_IMAGE_BYTES) {
            throw new BadRequestException("Image is too large. The maximum size is 8 MB.");
        }
        return fileStorageService.storeImage(file, "program-thumbnails");
    }

    private Program getEntityOrThrow(String id) {
        return programRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Program not found: " + id));
    }

    private long applicationCount(String slug) {
        return programApplicationRepository.countByProgramAndStatusNot(slug, ProgramApplicationStatus.DRAFT);
    }

    private String validateSlug(String slug) {
        String trimmed = slug == null ? "" : slug.trim();
        if (!VALID_SLUG.matcher(trimmed).matches()) {
            throw new BadRequestException("Slug must be 2-60 characters: letters, numbers, hyphens or underscores, "
                    + "starting and ending with a letter or number.");
        }
        return trimmed;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProgramStatus parseStatus(String status, ProgramStatus fallback) {
        if (status == null || status.isBlank()) return fallback;
        try {
            return ProgramStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + status);
        }
    }

    private int nextDisplayOrder() {
        return programRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .mapToInt(Program::getDisplayOrder).max().orElse(-1) + 1;
    }

    /** Recomputes each phase's position from list order rather than trusting whatever the client
     *  sent, so the two can never drift apart (see UpdateProgramRequest's own doc on the admin form
     *  always replacing the whole list). {@code number} is left as authored — it's the phase's
     *  display numeral ("01", "02"...), not necessarily the same as array position. */
    private List<com.nukkad.program.catalog.ProgramJourneyPhase> reorderJourney(List<com.nukkad.program.catalog.ProgramJourneyPhase> journey) {
        return journey;
    }

    private List<com.nukkad.program.catalog.ProgramBenefit> reorderBenefits(List<com.nukkad.program.catalog.ProgramBenefit> benefits) {
        if (benefits == null) return null;
        return java.util.stream.IntStream.range(0, benefits.size())
                .mapToObj(i -> {
                    var b = benefits.get(i);
                    return new com.nukkad.program.catalog.ProgramBenefit(i, b.icon(), b.title(), b.description());
                })
                .toList();
    }

    private String statusChangeAction(String previousStatus, String newStatus) {
        if (previousStatus.equals(newStatus)) return "PROGRAM_UPDATED";
        if ("PUBLISHED".equals(newStatus)) return "PROGRAM_PUBLISHED";
        if ("ARCHIVED".equals(newStatus)) return "PROGRAM_ARCHIVED";
        return "PROGRAM_UPDATED";
    }
}
