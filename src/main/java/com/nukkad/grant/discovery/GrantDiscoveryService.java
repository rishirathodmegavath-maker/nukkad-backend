package com.nukkad.grant.discovery;

import com.nukkad.grant.dto.DiscoveredGrantCandidate;
import com.nukkad.grant.entity.Grant;
import com.nukkad.grant.entity.GrantDiscoveryOrigin;
import com.nukkad.grant.entity.GrantProviderType;
import com.nukkad.grant.repository.GrantRepository;
import com.nukkad.grant.service.GrantService;
import com.nukkad.startup.entity.StartupStage;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates one scheduled discovery run: builds the prompt, calls Gemini, validates every
 * candidate the model returns, then creates or refreshes Grant rows.
 *
 * This pipeline auto-publishes with no admin-review step (an explicit product decision) -- the
 * validation below is therefore the only safety net standing between raw model output and the
 * live Grants list, and is deliberately strict: a candidate missing a real application URL, a
 * verifiable source URL, or a recognised provider type is dropped rather than guessed at, and an
 * already-expired deadline drops the candidate outright rather than publishing a closed scheme.
 */
@Service
public class GrantDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(GrantDiscoveryService.class);

    // These are Indian government/accelerator/corporate scheme deadlines -- a date-only deadline
    // means "valid through the end of that calendar day in India", not UTC midnight (which lands
    // ~5.5 hours into the *previous* IST day and would misclassify a same-day deadline as expired
    // almost as soon as it's discovered).
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    // Grant entity column limits (Grant.java) -- validated here so a too-long AI-generated value is
    // *rejected*, never silently truncated, and never left to throw a DataIntegrityViolationException
    // at saveAndFlush() time (which would otherwise abort every remaining candidate in the batch).
    private static final int NAME_MAX = 200;
    private static final int PROVIDER_MAX = 200;
    private static final int FUNDING_AMOUNT_MAX = 200;
    private static final int URL_MAX = 500;

    private final DiscoveryBatchCatalog batchCatalog;
    private final GrantDiscoveryPromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final GrantService grantService;
    private final GrantRepository grantRepository;
    private final GrantDiscoveryRunRepository runRepository;
    private final UserRepository userRepository;
    private final GrantDiscoveryProperties properties;
    private final ObjectMapper objectMapper;

    public GrantDiscoveryService(DiscoveryBatchCatalog batchCatalog, GrantDiscoveryPromptBuilder promptBuilder,
                                  GeminiClient geminiClient, GrantService grantService, GrantRepository grantRepository,
                                  GrantDiscoveryRunRepository runRepository, UserRepository userRepository,
                                  GrantDiscoveryProperties properties, ObjectMapper objectMapper) {
        this.batchCatalog = batchCatalog;
        this.promptBuilder = promptBuilder;
        this.geminiClient = geminiClient;
        this.grantService = grantService;
        this.grantRepository = grantRepository;
        this.runRepository = runRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public GrantDiscoveryRun runNextBatch() {
        DiscoveryBatch batch = batchCatalog.nextBatch();
        GrantDiscoveryRun run = GrantDiscoveryRun.builder()
                .batchGovernment(batch.government())
                .batchTopic(batch.topic())
                .status(DiscoveryRunStatus.FAILED)
                .startedAt(Instant.now())
                .build();

        try {
            String systemAdminId = resolveSystemAdminId();
            String prompt = promptBuilder.build(batch, LocalDate.now());
            String rawText = geminiClient.generateContent(prompt);
            List<DiscoveredGrantDto> raw = parseJsonArray(rawText);
            run.setSchemesFound(raw.size());

            int created = 0;
            int updated = 0;
            int rejected = 0;
            for (DiscoveredGrantDto candidateDto : raw) {
                DiscoveredGrantCandidate candidate = validate(candidateDto);
                if (candidate == null) {
                    rejected++;
                    continue;
                }

                // Each candidate is persisted in isolation: a single bad candidate (e.g. a DB
                // constraint this method's own validation didn't anticipate) must never abort the
                // remaining candidates in this same response, and must never leave the run's own
                // counters understating work that other candidates in the loop already completed.
                try {
                    Optional<Grant> existing = findExisting(candidate);
                    if (existing.isPresent()) {
                        grantService.refreshGrantFromDiscovery(existing.get().getId(), candidate, systemAdminId, batch.label());
                        updated++;
                    } else if (created < properties.maxNewGrantsPerRun()) {
                        grantService.createGrantFromDiscovery(candidate, systemAdminId, batch.label());
                        created++;
                    } else {
                        log.warn("Grant discovery hit its per-run cap ({}); dropping new candidate '{}'",
                                properties.maxNewGrantsPerRun(), candidate.name());
                        rejected++;
                    }
                } catch (Exception e) {
                    log.error("Grant discovery could not persist candidate '{}' in batch {}", candidate.name(), batch.label(), e);
                    rejected++;
                }
            }
            run.setSchemesCreated(created);
            run.setSchemesUpdated(updated);
            run.setSchemesRejected(rejected);
            run.setStatus(DiscoveryRunStatus.SUCCESS);
        } catch (Exception e) {
            log.error("Grant discovery run failed for batch {}", batch.label(), e);
            run.setErrorMessage(truncate(e.getMessage() == null ? e.toString() : e.getMessage()));
        } finally {
            run.setFinishedAt(Instant.now());
        }
        return runRepository.save(run);
    }

    /** Auto-hides every AI-discovered grant whose deadline has passed -- never touches a
     *  manually-entered grant. Reuses GrantService's own moderation-removal path (rather than
     *  flipping Grant fields directly) so this stays a normal, audited admin-style action. */
    public int hideExpiredAiDiscoveredGrants() {
        String systemAdminId = resolveSystemAdminId();
        List<Grant> expired = grantRepository.findByDiscoveryOriginAndDeadlineBeforeAndRemovedByAdminFalse(
                GrantDiscoveryOrigin.AI_DISCOVERY, Instant.now());
        for (Grant grant : expired) {
            grantService.setRemovedByAdmin(systemAdminId, grant.getId(), true,
                    "Application deadline passed (auto-hidden by the discovery pipeline)", "internal:grant-discovery-sweep");
        }
        return expired.size();
    }

    private String resolveSystemAdminId() {
        List<User> admins = userRepository.findByRoleOrderByCreatedAtAsc(SecurityRole.ADMIN, PageRequest.of(0, 1)).getContent();
        if (admins.isEmpty()) {
            throw new IllegalStateException("No ADMIN account exists to attribute AI-discovered grants to");
        }
        return admins.get(0).getId();
    }

    private List<DiscoveredGrantDto> parseJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end < start) {
            throw new IllegalStateException("Gemini response did not contain a JSON array: " + truncate(text));
        }
        String json = text.substring(start, end + 1);
        DiscoveredGrantDto[] parsed = objectMapper.readValue(json, DiscoveredGrantDto[].class);
        return List.of(parsed);
    }

    /** Returns null (reject the whole candidate) when it fails a hard requirement; otherwise a
     *  cleaned, typed candidate ready to persist. */
    private DiscoveredGrantCandidate validate(DiscoveredGrantDto dto) {
        String name = trimToNull(dto.grantSchemeName());
        String provider = trimToNull(dto.provider());
        if (name == null || provider == null) return null;
        if (name.length() > NAME_MAX || provider.length() > PROVIDER_MAX) return null;

        GrantProviderType providerType = parseProviderType(dto.providerType());
        if (providerType == null) return null;

        String applicationUrl = normalizeUrlOrNull(dto.applicationUrl());
        if (applicationUrl == null || applicationUrl.length() > URL_MAX) return null;

        // No admin review step downstream -- an opportunity with no verifiable official source is
        // never auto-published, full stop.
        String sourceUrl = normalizeUrlOrNull(dto.sourceUrl());
        if (sourceUrl == null || sourceUrl.length() > URL_MAX) return null;

        String fundingAmount = trimToNull(dto.fundingAmount());
        if (fundingAmount != null && fundingAmount.length() > FUNDING_AMOUNT_MAX) return null;

        Instant deadline;
        try {
            deadline = parseDeadlineOrNull(dto.applicationDeadline());
        } catch (DateTimeParseException e) {
            deadline = null; // malformed date -> treat as rolling rather than reject the whole record
        }
        if (deadline != null && deadline.isBefore(Instant.now())) {
            return null; // already expired -- never auto-publish a closed opportunity
        }

        Set<StartupStage> stages = new HashSet<>();
        if (dto.eligibleStages() != null) {
            for (String label : dto.eligibleStages()) {
                if (label == null) continue;
                try {
                    stages.add(StartupStage.fromLabel(label.trim()));
                } catch (IllegalArgumentException ignored) {
                    // an unrecognised stage label is dropped, not guessed at
                }
            }
        }

        Set<String> sectors = new HashSet<>();
        if (dto.eligibleSectors() != null) {
            for (String sector : dto.eligibleSectors()) {
                String trimmed = trimToNull(sector);
                if (trimmed != null) sectors.add(trimmed);
            }
        }

        return new DiscoveredGrantCandidate(
                name, provider, providerType,
                trimToNull(dto.description()),
                fundingAmount,
                trimToNull(dto.eligibilityCriteria()),
                sectors, stages, deadline, applicationUrl, sourceUrl
        );
    }

    /** Only ever matches a grant the discovery pipeline itself created (discoveryOrigin=AI_DISCOVERY)
     *  -- a manually/admin-created grant that happens to share an application URL or a name+provider
     *  with a discovered candidate is never returned here, so it can never be refreshed/overwritten
     *  below. A coincidental match against a manual grant is simply treated as "no existing AI row",
     *  i.e. as a fresh discovery candidate (subject to the normal per-run new-grant cap). */
    private Optional<Grant> findExisting(DiscoveredGrantCandidate candidate) {
        return grantRepository.findFirstByApplicationUrlIgnoreCaseAndDiscoveryOrigin(candidate.applicationUrl(), GrantDiscoveryOrigin.AI_DISCOVERY)
                .or(() -> grantRepository.findFirstByNameIgnoreCaseAndProviderIgnoreCaseAndDiscoveryOrigin(
                        candidate.name(), candidate.provider(), GrantDiscoveryOrigin.AI_DISCOVERY));
    }

    private GrantProviderType parseProviderType(String label) {
        if (label == null || label.isBlank()) return null;
        String normalized = label.trim();
        if (normalized.equalsIgnoreCase("others")) normalized = "Other"; // the prompt's schema says "Others"; the entity's enum label is "Other"
        try {
            return GrantProviderType.fromLabel(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A date-only deadline (e.g. "2026-09-23") is valid through the END of that calendar day in
     *  India Standard Time, not UTC midnight -- these are Indian scheme deadlines, and comparing
     *  against UTC start-of-day would treat a same-day deadline as already expired almost as soon
     *  as it's discovered (UTC midnight lands 5.5 hours into the *previous* IST day). */
    private Instant parseDeadlineOrNull(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return null;
        return LocalDate.parse(isoDate.trim()).atTime(LocalTime.MAX).atZone(INDIA_ZONE).toInstant();
    }

    /** Mirrors GrantService's own applicationUrl normalisation. Kept as a small local copy rather
     *  than widening that class's (currently private) surface just to reuse four lines. */
    private String normalizeUrlOrNull(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isEmpty()) return null;
        String candidate = trimmed.matches("(?i)^https?://.*") ? trimmed : "https://" + trimmed;
        try {
            URI uri = new URI(candidate);
            if (uri.getHost() == null || uri.getHost().isBlank()) return null;
        } catch (Exception e) {
            return null;
        }
        return candidate;
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase("null") ? null : trimmed;
    }

    private String truncate(String s) {
        return s.length() > 1000 ? s.substring(0, 1000) + "…" : s;
    }
}
