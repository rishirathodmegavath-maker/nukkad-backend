package com.nukkad.industry.service;

import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.grant.repository.GrantRepository;
import com.nukkad.grant.repository.GrantSpecifications;
import com.nukkad.industry.dto.IndustryDetailDto;
import com.nukkad.industry.dto.IndustryDto;
import com.nukkad.investor.repository.InvestorRepository;
import com.nukkad.investor.repository.InvestorSpecifications;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Industry Analysis is deliberately not backed by its own table. Every "industry" here is a
 * distinct sector spelling already present in real data (startups, catalog investors), folded
 * case-insensitively the same way {@code StartupService.listSectors} already folds "AI"/"ai" —
 * there is no separate curated taxonomy to keep in sync, and every number shown is derived from
 * live rows, never invented.
 */
@Service
public class IndustryService {

    private final StartupRepository startupRepository;
    private final InvestorRepository investorRepository;
    private final GrantRepository grantRepository;

    public IndustryService(StartupRepository startupRepository, InvestorRepository investorRepository,
                            GrantRepository grantRepository) {
        this.startupRepository = startupRepository;
        this.investorRepository = investorRepository;
        this.grantRepository = grantRepository;
    }

    @Transactional(readOnly = true)
    public List<IndustryDto> listIndustries(String viewerId, String q) {
        Discovered discovered = discover(viewerId != null);
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        return discovered.spelling.keySet().stream()
                .map(key -> new IndustryDto(discovered.spelling.get(key), slugify(discovered.spelling.get(key)),
                        discovered.startupTotals.getOrDefault(key, 0L)))
                .filter(dto -> needle == null || needle.isBlank() || dto.name().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparingLong(IndustryDto::startupCount).reversed()
                        .thenComparing(d -> d.name().toLowerCase(Locale.ROOT)))
                .limit(50)
                .toList();
    }

    @Transactional(readOnly = true)
    public IndustryDetailDto getIndustry(String slug, String viewerId) {
        Discovered discovered = discover(viewerId != null);
        String resolvedName = discovered.spelling.values().stream()
                .filter(name -> slugify(name).equals(slug))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Unknown industry: " + slug));

        List<Startup> matches = startupRepository.findAll(StartupSpecifications.combine(
                StartupSpecifications.sector(resolvedName),
                StartupSpecifications.notRemoved(),
                StartupSpecifications.approved(),
                StartupSpecifications.visibleTo(viewerId != null)));

        Map<String, Long> stageDistribution = matches.stream()
                .collect(Collectors.groupingBy(s -> s.getStage().getLabel(), Collectors.counting()));

        Instant now = Instant.now();
        Instant ninetyDaysAgo = now.minus(Duration.ofDays(90));
        Instant oneEightyDaysAgo = now.minus(Duration.ofDays(180));
        long recentStartupCount = matches.stream().filter(s -> !s.getCreatedAt().isBefore(ninetyDaysAgo)).count();
        long priorStartupCount = matches.stream()
                .filter(s -> s.getCreatedAt().isBefore(ninetyDaysAgo) && !s.getCreatedAt().isBefore(oneEightyDaysAgo))
                .count();

        long investorCount = investorRepository.count(InvestorSpecifications.combine(
                InvestorSpecifications.publiclyVisible(),
                InvestorSpecifications.sector(resolvedName)));

        long grantCount = grantRepository.count(GrantSpecifications.combine(
                GrantSpecifications.notRemoved(),
                GrantSpecifications.approved(),
                GrantSpecifications.notExpired(),
                GrantSpecifications.sector(resolvedName)));

        return new IndustryDetailDto(resolvedName, slugify(resolvedName), matches.size(), investorCount, grantCount,
                stageDistribution, recentStartupCount, priorStartupCount, now);
    }

    /** Folds sector spellings from real Startup and catalog-Investor data into one lowercase-keyed
     *  vocabulary, picking whichever spelling has the strongest real evidence behind it — exactly
     *  the approach {@code StartupService.listSectors} already validated for this exact
     *  free-text-inconsistency problem. */
    private Discovered discover(boolean includeMembersOnly) {
        Map<String, Long> startupTotals = new HashMap<>();
        Map<String, String> spelling = new HashMap<>();
        Map<String, Long> spellingWeight = new HashMap<>();

        for (Object[] row : startupRepository.countBySector(includeMembersOnly)) {
            String raw = ((String) row[0]).trim();
            long count = (Long) row[1];
            String key = raw.toLowerCase(Locale.ROOT);
            startupTotals.merge(key, count, Long::sum);
            if (count > spellingWeight.getOrDefault(key, 0L)) {
                spellingWeight.put(key, count);
                spelling.put(key, raw);
            }
        }
        for (String raw : investorRepository.findDistinctVisibleSectors()) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            String key = trimmed.toLowerCase(Locale.ROOT);
            spellingWeight.putIfAbsent(key, 0L);
            spelling.putIfAbsent(key, trimmed);
        }
        return new Discovered(spelling, startupTotals);
    }

    private static String slugify(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isEmpty() ? "industry" : normalized;
    }

    private record Discovered(Map<String, String> spelling, Map<String, Long> startupTotals) {
    }
}
