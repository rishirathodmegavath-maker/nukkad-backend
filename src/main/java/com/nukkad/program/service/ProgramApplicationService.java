package com.nukkad.program.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.program.catalog.ProgramCatalog;
import com.nukkad.program.catalog.ProgramField;
import com.nukkad.program.dto.ProgramApplicationDto;
import com.nukkad.program.entity.Program;
import com.nukkad.program.entity.ProgramApplication;
import com.nukkad.program.entity.ProgramApplicationStatus;
import com.nukkad.program.mapper.ProgramMapper;
import com.nukkad.program.repository.ProgramApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The applicant-facing half of the program application workflow — start/resume a draft, save
 * progress, submit, withdraw. {@link com.nukkad.admin.service.AdminProgramApplicationService} is
 * the review-facing half. Mirrors {@code InvestorActivationService}'s "at most one active
 * application, reapply after rejection" shape, generalized per-program and with a real draft/resume
 * stage that investor activation (a single-shot submit) never needed.
 */
@Service
public class ProgramApplicationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+()\\-\\s]{7,20}$");
    private static final String MULTISELECT_DELIMITER = "|";
    private static final int MAX_ANSWER_LENGTH = 4000;

    private final ProgramApplicationRepository programApplicationRepository;
    private final ProgramMapper programMapper;

    public ProgramApplicationService(ProgramApplicationRepository programApplicationRepository, ProgramMapper programMapper) {
        this.programApplicationRepository = programApplicationRepository;
        this.programMapper = programMapper;
    }

    /** One row per program — the latest application, whatever its status, so "My Applications"
     *  always shows where things currently stand (including a past rejection). */
    @Transactional(readOnly = true)
    public List<ProgramApplicationDto> getMine(String userId) {
        Map<Program, ProgramApplication> latestByProgram = new LinkedHashMap<>();
        for (ProgramApplication application : programApplicationRepository.findByApplicantUserIdOrderByCreatedAtAsc(userId)) {
            latestByProgram.put(application.getProgram(), application); // later rows overwrite earlier ones
        }
        return latestByProgram.values().stream().map(programMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProgramApplicationDto getMineForProgram(String userId, String programKey) {
        Program program = ProgramService.parseProgram(programKey);
        ProgramApplication application = programApplicationRepository
                .findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc(userId, program)
                .orElseThrow(() -> new ResourceNotFoundException("You haven't started an application for " + program.name()));
        return programMapper.toDto(application);
    }

    /** Creates a fresh draft if none exists yet, or the latest one ended in REJECTED/WITHDRAWN;
     *  otherwise returns/continues the existing draft. Refuses to touch anything already
     *  submitted — that's Admin's to move forward, not the applicant's to keep editing. */
    @Transactional
    public ProgramApplicationDto saveDraft(String userId, String programKey, Map<String, String> answers) {
        Program program = ProgramService.parseProgram(programKey);
        ProgramApplication application = findOrStartDraft(userId, program);
        mergeAnswers(application, program, answers);
        return programMapper.toDto(programApplicationRepository.saveAndFlush(application));
    }

    @Transactional
    public ProgramApplicationDto submit(String userId, String programKey) {
        Program program = ProgramService.parseProgram(programKey);
        ProgramApplication application = programApplicationRepository
                .findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc(userId, program)
                .filter(a -> a.getApplicantUserId().equals(userId))
                .orElseThrow(() -> new BadRequestException("Start an application before submitting it"));
        if (application.getStatus() != ProgramApplicationStatus.DRAFT) {
            throw new ConflictException("This application has already been submitted");
        }

        validateRequiredFields(program, application.getAnswers());
        application.setStatus(ProgramApplicationStatus.SUBMITTED);
        application.setSubmittedAt(java.time.Instant.now());
        return programMapper.toDto(programApplicationRepository.saveAndFlush(application));
    }

    @Transactional
    public ProgramApplicationDto withdraw(String userId, String id) {
        ProgramApplication application = programApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
        if (!application.getApplicantUserId().equals(userId)) {
            throw new ForbiddenException("You can only withdraw your own application");
        }
        if (application.getStatus() == ProgramApplicationStatus.SELECTED
                || application.getStatus() == ProgramApplicationStatus.REJECTED
                || application.getStatus() == ProgramApplicationStatus.WITHDRAWN) {
            throw new ConflictException("This application can no longer be withdrawn");
        }
        application.setStatus(ProgramApplicationStatus.WITHDRAWN);
        return programMapper.toDto(programApplicationRepository.saveAndFlush(application));
    }

    private ProgramApplication findOrStartDraft(String userId, Program program) {
        var latest = programApplicationRepository.findTopByApplicantUserIdAndProgramOrderByCreatedAtDesc(userId, program);
        if (latest.isEmpty()) {
            return ProgramApplication.builder().applicantUserId(userId).program(program).status(ProgramApplicationStatus.DRAFT).build();
        }
        ProgramApplication application = latest.get();
        if (application.getStatus() == ProgramApplicationStatus.DRAFT) {
            return application;
        }
        if (application.getStatus() == ProgramApplicationStatus.REJECTED || application.getStatus() == ProgramApplicationStatus.WITHDRAWN) {
            return ProgramApplication.builder().applicantUserId(userId).program(program).status(ProgramApplicationStatus.DRAFT).build();
        }
        throw new ConflictException("You already have an application for this program under review");
    }

    /** Merges into the existing answers rather than replacing them, so autosaving one step never
     *  loses answers already saved from another. Rejects unknown field keys (defends the free-form
     *  map against arbitrary payloads) and validates shape (email/phone/date/url/select options) for
     *  any non-blank value, so a bad value is caught at save time rather than only at submit. */
    private void mergeAnswers(ProgramApplication application, Program program, Map<String, String> answers) {
        if (answers == null || answers.isEmpty()) return;
        Map<String, ProgramField> fieldsByKey = fieldsByKey(program);
        for (Map.Entry<String, String> entry : answers.entrySet()) {
            ProgramField field = fieldsByKey.get(entry.getKey());
            if (field == null) {
                throw new BadRequestException("Unknown application field: " + entry.getKey());
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.length() > MAX_ANSWER_LENGTH) {
                throw new BadRequestException(field.label() + " is too long");
            }
            if (!value.isEmpty()) {
                validateFieldFormat(field, value);
            }
            application.getAnswers().put(field.key(), value);
        }
    }

    private void validateFieldFormat(ProgramField field, String value) {
        switch (field.type()) {
            case EMAIL -> {
                if (!EMAIL_PATTERN.matcher(value).matches()) throw new BadRequestException(field.label() + " must be a valid email address");
            }
            case PHONE -> {
                if (!PHONE_PATTERN.matcher(value).matches()) throw new BadRequestException(field.label() + " must be a valid phone number");
            }
            case DATE -> {
                try {
                    LocalDate.parse(value);
                } catch (DateTimeParseException e) {
                    throw new BadRequestException(field.label() + " must be a valid date");
                }
            }
            case URL -> {
                try {
                    URI uri = new URI(value);
                    String scheme = uri.getScheme();
                    boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
                    if (!web || uri.getHost() == null || uri.getHost().isBlank()) {
                        throw new BadRequestException(field.label() + " must start with http:// or https://");
                    }
                } catch (URISyntaxException e) {
                    throw new BadRequestException(field.label() + " must be a valid URL");
                }
            }
            case SELECT -> {
                if (!field.options().contains(value)) throw new BadRequestException("Invalid value for " + field.label());
            }
            case MULTISELECT -> {
                Set<String> options = Set.copyOf(field.options());
                for (String option : value.split(Pattern.quote(MULTISELECT_DELIMITER))) {
                    if (!option.isBlank() && !options.contains(option)) {
                        throw new BadRequestException("Invalid value for " + field.label());
                    }
                }
            }
            case TEXT, TEXTAREA -> { /* free text, already length-checked above */ }
        }
    }

    private void validateRequiredFields(Program program, Map<String, String> answers) {
        List<String> missing = fieldsByKey(program).values().stream()
                .filter(ProgramField::required)
                .filter(f -> answers.get(f.key()) == null || answers.get(f.key()).isBlank())
                .map(ProgramField::label)
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Please complete: " + String.join(", ", missing));
        }
    }

    private Map<String, ProgramField> fieldsByKey(Program program) {
        Map<String, ProgramField> byKey = new TreeMap<>();
        ProgramCatalog.get(program).applicationSteps().forEach(step -> step.fields().forEach(f -> byKey.put(f.key(), f)));
        return byKey;
    }
}
