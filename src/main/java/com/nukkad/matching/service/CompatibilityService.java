package com.nukkad.matching.service;

import com.nukkad.matching.RecommendationWeights;
import com.nukkad.matching.TopKSelector;
import com.nukkad.matching.dto.CofounderMatchDto;
import com.nukkad.matching.graph.CandidatePoolService;
import com.nukkad.matching.graph.CandidatePoolService.CandidatePool;
import com.nukkad.matching.graph.EgoNetworkService.EgoNetwork;
import com.nukkad.matching.text.CosineSimilarity;
import com.nukkad.matching.text.TfIdfVectorizer;
import com.nukkad.user.entity.LookingFor;
import com.nukkad.user.entity.OpenTo;
import com.nukkad.user.entity.User;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Co-founder / collaborator compatibility — distinguishes genuine complementarity (do the two people's
 * skillsets cover different ground, so each fills the other's gap?) from raw similarity (shared TF-IDF
 * profile text), blended with mutual connections and lookingFor/openTo overlap. Candidate pool is the
 * same 2-hop ego network as "People You May Know" ({@link CandidatePoolService}) — only the scoring
 * differs. See {@link RecommendationWeights} (the {@code COFOUNDER_*} constants) for the weighting
 * rationale.
 */
@Service
public class CompatibilityService {

    /**
     * Broad skill domains used only to measure complementarity — deliberately coarse (a handful of
     * categories, not a taxonomy) since the goal is "different ground vs. same ground", not precise
     * skill classification.
     */
    private enum SkillCategory {
        TECHNICAL("engineering", "backend", "frontend", "full stack", "fullstack", "ai", "ml", "machine learning",
                "data science", "software", "developer", "programming", "devops", "cloud", "mobile", "ios",
                "android", "blockchain", "cybersecurity", "computer vision", "data engineering"),
        BUSINESS("business", "strategy", "operations", "finance", "fundraising", "biz dev", "business development",
                "sales", "legal", "accounting"),
        MARKETING("marketing", "growth", "seo", "content", "branding", "social media", "advertising", "communications"),
        PRODUCT("product", "product management", "product strategy", "user research"),
        DESIGN("design", "ui", "ux", "graphic design", "visual design");

        private final List<String> keywords;

        SkillCategory(String... keywords) {
            this.keywords = List.of(keywords);
        }

        boolean matches(String skillLowercase) {
            for (String keyword : keywords) {
                if (skillLowercase.contains(keyword)) return true;
            }
            return false;
        }
    }

    private record ScoredMatch(User user, double score, int mutualConnections, List<String> reasons) {
    }

    private final CandidatePoolService candidatePoolService;
    private final UserService userService;
    private final UserMapper userMapper;

    public CompatibilityService(CandidatePoolService candidatePoolService, UserService userService, UserMapper userMapper) {
        this.candidatePoolService = candidatePoolService;
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public List<CofounderMatchDto> findMatches(String viewerId, int limit) {
        User viewer = userService.getEntityOrThrow(viewerId);
        int candidatePoolSize = Math.max(limit, Math.min(
                limit * RecommendationWeights.COFOUNDER_CANDIDATE_POOL_MULTIPLIER,
                RecommendationWeights.COFOUNDER_CANDIDATE_POOL_MAX));
        CandidatePool pool = candidatePoolService.gather(viewer, candidatePoolSize);
        if (pool.allCandidates().isEmpty()) return List.of();

        String viewerDoc = ProfileTextBuilder.forUser(viewer);
        List<String> corpus = new ArrayList<>();
        corpus.add(viewerDoc);
        Map<String, String> docsByCandidateId = new HashMap<>();
        for (User candidate : pool.allCandidates()) {
            String doc = ProfileTextBuilder.forUser(candidate);
            docsByCandidateId.put(candidate.getId(), doc);
            corpus.add(doc);
        }
        Map<String, Double> idf = TfIdfVectorizer.fit(corpus);
        Map<String, Double> viewerVector = TfIdfVectorizer.vectorize(viewerDoc, idf);
        Set<SkillCategory> viewerCategories = categoriesFor(viewer.getSkills());

        List<ScoredMatch> scored = new ArrayList<>(pool.allCandidates().size());
        for (User candidate : pool.allCandidates()) {
            scored.add(score(viewer, viewerCategories, viewerVector, idf, candidate, pool.network(),
                    pool.networkCandidateIds().contains(candidate.getId()), docsByCandidateId));
        }

        List<ScoredMatch> qualified = scored.stream()
                .filter(s -> s.score() >= RecommendationWeights.COFOUNDER_MIN_QUALITY_SCORE && !s.reasons().isEmpty())
                .toList();

        List<ScoredMatch> top = TopKSelector.topK(qualified, limit, Comparator.comparingDouble(ScoredMatch::score));
        return top.stream().map(this::toDto).toList();
    }

    private ScoredMatch score(User viewer, Set<SkillCategory> viewerCategories, Map<String, Double> viewerVector,
                               Map<String, Double> idf, User candidate, EgoNetwork network, boolean isNetworkCandidate,
                               Map<String, String> docsByCandidateId) {
        int mutualConnections = isNetworkCandidate
                ? network.mutualConnectionCount(candidate.getId())
                : userService.getMutualConnections(viewer.getId(), candidate.getId(), 1).totalCount();

        Set<SkillCategory> candidateCategories = categoriesFor(candidate.getSkills());
        double complementarity = complementarity(viewerCategories, candidateCategories);

        double profileSimilarity = CosineSimilarity.compute(viewerVector,
                TfIdfVectorizer.vectorize(docsByCandidateId.get(candidate.getId()), idf));

        double mutualScore = Math.min(mutualConnections / RecommendationWeights.MUTUAL_CONNECTIONS_NORMALIZATION_CAP, 1.0);
        double lookingForOpenToScore = lookingForOpenToOverlap(viewer, candidate);

        double score = complementarity * RecommendationWeights.COFOUNDER_WEIGHT_COMPLEMENTARITY
                + profileSimilarity * RecommendationWeights.COFOUNDER_WEIGHT_PROFILE_SIMILARITY
                + mutualScore * RecommendationWeights.COFOUNDER_WEIGHT_MUTUAL_CONNECTIONS
                + lookingForOpenToScore * RecommendationWeights.COFOUNDER_WEIGHT_LOOKING_FOR_OPEN_TO;

        List<String> reasons = buildReasons(viewerCategories, candidateCategories, mutualConnections, lookingForOpenToScore);
        return new ScoredMatch(candidate, score, mutualConnections, reasons);
    }

    /**
     * 1.0 when the two skillsets cover entirely different ground (true complementarity — each fills the
     * other's gap); 0.0 when they cover identical ground (pure overlap, no gap-filling) or when either
     * side has no categorizable skills at all (no signal, not fabricated).
     */
    private double complementarity(Set<SkillCategory> a, Set<SkillCategory> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<SkillCategory> union = EnumSet.copyOf(a);
        union.addAll(b);
        Set<SkillCategory> intersection = EnumSet.copyOf(a);
        intersection.retainAll(b);
        int symmetricDifference = union.size() - intersection.size();
        return (double) symmetricDifference / union.size();
    }

    private Set<SkillCategory> categoriesFor(Set<String> skills) {
        Set<SkillCategory> categories = EnumSet.noneOf(SkillCategory.class);
        for (String skill : skills) {
            String lower = skill.toLowerCase();
            for (SkillCategory category : SkillCategory.values()) {
                if (category.matches(lower)) categories.add(category);
            }
        }
        return categories;
    }

    private double lookingForOpenToOverlap(User viewer, User candidate) {
        int overlap = crossLabelOverlap(viewer.getLookingFor(), candidate.getOpenTo())
                + crossLabelOverlap(candidate.getLookingFor(), viewer.getOpenTo());
        return Math.min(overlap / 2.0, 1.0);
    }

    private int crossLabelOverlap(Set<LookingFor> lookingFor, Set<OpenTo> openTo) {
        Set<String> labels = new HashSet<>();
        for (LookingFor lf : lookingFor) labels.add(lf.getLabel().toLowerCase());
        int count = 0;
        for (OpenTo ot : openTo) {
            if (labels.contains(ot.getLabel().toLowerCase())) count++;
        }
        return count;
    }

    private List<String> buildReasons(Set<SkillCategory> viewerCategories, Set<SkillCategory> candidateCategories,
                                       int mutualConnections, double lookingForOpenToScore) {
        List<String> reasons = new ArrayList<>();
        Set<SkillCategory> theirsOnly = EnumSet.noneOf(SkillCategory.class);
        theirsOnly.addAll(candidateCategories);
        theirsOnly.removeAll(viewerCategories);
        if (!theirsOnly.isEmpty()) {
            reasons.add("Brings " + labelJoin(theirsOnly) + " skills you don't have");
        }
        if (mutualConnections > 0) {
            reasons.add(mutualConnections + " mutual connection" + (mutualConnections == 1 ? "" : "s"));
        }
        if (lookingForOpenToScore > 0) reasons.add("Compatible looking-for / open-to interests");
        return reasons;
    }

    private String labelJoin(Set<SkillCategory> categories) {
        List<String> labels = new ArrayList<>();
        for (SkillCategory c : categories) labels.add(capitalize(c.name()));
        Collections.sort(labels);
        return String.join(" & ", labels);
    }

    private String capitalize(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private CofounderMatchDto toDto(ScoredMatch match) {
        return new CofounderMatchDto(
                userMapper.toDto(match.user(), "NONE", false),
                Math.round(match.score() * 10000.0) / 10000.0,
                match.mutualConnections(),
                match.reasons()
        );
    }
}
