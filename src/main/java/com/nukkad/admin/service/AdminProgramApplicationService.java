package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminChangeProgramApplicationStatusRequest;
import com.nukkad.admin.dto.AdminProgramApplicationDto;
import com.nukkad.admin.mapper.AdminMapper;
import com.nukkad.admin.util.AdminPaging;
import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramApplication;
import com.nukkad.program.entity.ProgramApplicationStatus;
import com.nukkad.program.repository.ProgramApplicationRepository;
import com.nukkad.program.repository.ProgramApplicationSpecifications;
import com.nukkad.program.service.ProgramService;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.repository.UserSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The review-facing half of the program application workflow — list/search/filter, open one,
 *  change its status. See {@link com.nukkad.program.service.ProgramApplicationService} for the
 *  applicant-facing half. Reuses the existing {@code ADMIN_ACTION} audit literal rather than
 *  minting new ones: this feature doesn't need its own native-ENUM widening migration on top of
 *  the one that already caused a production incident earlier the same day this was built. */
@Service
public class AdminProgramApplicationService {

    /** From → the statuses Admin may move it to. DRAFT and WITHDRAWN are reachable only by the
     *  applicant themselves (starting a draft / withdrawing) — never a status Admin sets. */
    private static final Set<ProgramApplicationStatus> ADMIN_ASSIGNABLE = Set.of(
            ProgramApplicationStatus.UNDER_REVIEW, ProgramApplicationStatus.SHORTLISTED,
            ProgramApplicationStatus.SELECTED, ProgramApplicationStatus.REJECTED);

    private final ProgramApplicationRepository programApplicationRepository;
    private final UserRepository userRepository;
    private final AdminMapper adminMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AdminProgramApplicationService(ProgramApplicationRepository programApplicationRepository,
                                           UserRepository userRepository, AdminMapper adminMapper,
                                           AuditService auditService, NotificationService notificationService) {
        this.programApplicationRepository = programApplicationRepository;
        this.userRepository = userRepository;
        this.adminMapper = adminMapper;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Page<AdminProgramApplicationDto> list(String programKey, String statusRaw, String q, int page, int size) {
        Program program = programKey == null || programKey.isBlank() ? null : ProgramService.parseProgram(programKey);
        ProgramApplicationStatus status = parseOptionalStatus(statusRaw);
        Pageable pageable = PageRequest.of(page, AdminPaging.clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));

        List<String> matchingApplicantIds = null;
        if (q != null && !q.isBlank()) {
            matchingApplicantIds = userRepository.findAll(UserSpecifications.adminSearch(q)).stream().map(User::getId).toList();
            if (matchingApplicantIds.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
        }

        var spec = ProgramApplicationSpecifications.combine(
                ProgramApplicationSpecifications.program(program),
                ProgramApplicationSpecifications.status(status),
                ProgramApplicationSpecifications.applicantIn(matchingApplicantIds));
        Page<ProgramApplication> applications = programApplicationRepository.findAll(spec, pageable);
        Map<String, User> users = fetchReferencedUsers(applications.getContent());
        return applications.map(a -> adminMapper.toDto(a, users));
    }

    @Transactional(readOnly = true)
    public AdminProgramApplicationDto get(String id) {
        ProgramApplication application = getEntityOrThrow(id);
        return adminMapper.toDto(application, fetchReferencedUsers(List.of(application)));
    }

    @Transactional
    public AdminProgramApplicationDto changeStatus(String adminId, String id, AdminChangeProgramApplicationStatusRequest request, String ip) {
        ProgramApplication application = getEntityOrThrow(id);
        if (application.getStatus() == ProgramApplicationStatus.DRAFT) {
            throw new ConflictException("This application hasn't been submitted yet");
        }
        if (application.getStatus() == ProgramApplicationStatus.WITHDRAWN) {
            throw new ConflictException("This application was withdrawn by the applicant");
        }
        ProgramApplicationStatus newStatus = parseAssignableStatus(request.status());

        application.setStatus(newStatus);
        application.setAdminNote(request.note());
        application.setReviewedBy(adminId);
        application.setReviewedAt(Instant.now());
        application = programApplicationRepository.saveAndFlush(application);

        auditService.log(adminId, AuditAction.ADMIN_ACTION, "ProgramApplication", id, ip,
                Map.of("action", "PROGRAM_APPLICATION_STATUS_CHANGED", "status", newStatus.name()));
        notificationService.notify(application.getApplicantUserId(), NotificationType.program_application,
                "Your " + application.getProgram().name() + " application was updated",
                statusMessage(newStatus, application.getProgram().name()), application.getId(), adminId);

        return adminMapper.toDto(application, fetchReferencedUsers(List.of(application)));
    }

    private String statusMessage(ProgramApplicationStatus status, String programName) {
        return switch (status) {
            case UNDER_REVIEW -> "Your application is now under review.";
            case SHORTLISTED -> "You've been shortlisted for " + programName + ".";
            case SELECTED -> "Congratulations — you've been selected for " + programName + "!";
            case REJECTED -> "Your application was not selected this time.";
            default -> "Your application status changed to " + status.name() + ".";
        };
    }

    private ProgramApplicationStatus parseAssignableStatus(String status) {
        ProgramApplicationStatus parsed;
        try {
            parsed = ProgramApplicationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + status);
        }
        if (!ADMIN_ASSIGNABLE.contains(parsed)) {
            throw new BadRequestException("Admin cannot set status to " + parsed.name());
        }
        return parsed;
    }

    private ProgramApplicationStatus parseOptionalStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return ProgramApplicationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + status);
        }
    }

    private ProgramApplication getEntityOrThrow(String id) {
        return programApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    private Map<String, User> fetchReferencedUsers(List<ProgramApplication> applications) {
        Set<String> ids = new HashSet<>();
        for (ProgramApplication a : applications) {
            ids.add(a.getApplicantUserId());
            if (a.getReviewedBy() != null) ids.add(a.getReviewedBy());
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
    }
}
