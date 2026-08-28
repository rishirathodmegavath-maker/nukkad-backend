package com.nukkad.matching.service;

import com.nukkad.matching.RecommendationWeights;
import com.nukkad.matching.TopKSelector;
import com.nukkad.matching.dto.RecommendedUserDto;
import com.nukkad.matching.graph.CandidatePoolService;
import com.nukkad.matching.graph.CandidatePoolService.CandidatePool;
import com.nukkad.matching.graph.EgoNetworkService.EgoNetwork;
import com.nukkad.matching.text.CosineSimilarity;
import com.nukkad.matching.text.TfIdfVectorizer;
import com.nukkad.user.entity.LookingFor;
import com.nukkad.user.entity.OpenTo;
import com.nukkad.user.entity.User;
import com.nukkad.user.mapper.UserMapper;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "People You May Know" — real 2-hop graph traversal (via {@link CandidatePoolService}) plus a weighted,
 * multi-signal score, ranked with a bounded min-heap ({@link TopKSelector}) rather than sorting every
 * candidate. See {@link RecommendationWeights} for the scoring constants and their rationale.
 */
@Service
public class PeopleRecommendationService {

    private record ScoredCandidate(User user, double score, int mutualConnections, List<String> commonSkills,
                                    Integer graphDistance, List<String> reasons) {
    }

    private final CandidatePoolService candidatePoolService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserService userService;

    public PeopleRecommendationService(CandidatePoolService candidatePoolService, UserRepository userRepository,
                                        UserMapper userMapper, UserService userService) {
        this.candidatePoolService = candidatePoolService;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<RecommendedUserDto> recommend(String viewerId, int limit) {
        User viewer = userService.getEntityOrThrow(viewerId);
        CandidatePool pool = candidatePoolService.gather(viewer, limit);
        EgoNetwork network = pool.network();
        Set<String> networkCandidateIds = pool.networkCandidateIds();
        List<User> allCandidates = pool.allCandidates();
        if (allCandidates.isEmpty()) return List.of();

        Set<String> allCandidateIds = new LinkedHashSet<>();
        for (User u : allCandidates) allCandidateIds.add(u.getId());
        Map<String, Set<String>> skillsByUserId = bulkFetchSkills(allCandidateIds);
        Set<String> viewerSkills = normalizedSkills(viewer.getSkills());

        String viewerDoc = ProfileTextBuilder.forUser(viewer);
        List<String> corpus = new ArrayList<>();
        corpus.add(viewerDoc);
        Map<String, String> docsByCandidateId = new HashMap<>();
        for (User candidate : allCandidates) {
            String doc = ProfileTextBuilder.forUser(candidate);
            docsByCandidateId.put(candidate.getId(), doc);
            corpus.add(doc);
        }
        Map<String, Double> idf = TfIdfVectorizer.fit(corpus);
        Map<String, Double> viewerVector = TfIdfVectorizer.vectorize(viewerDoc, idf);

        List<ScoredCandidate> scored = new ArrayList<>(allCandidates.size());
        for (User candidate : allCandidates) {
            scored.add(score(viewer, viewerSkills, viewerVector, idf, candidate,
                    network, networkCandidateIds.contains(candidate.getId()), skillsByUserId, docsByCandidateId));
        }

        List<ScoredCandidate> top = TopKSelector.topK(scored, limit, Comparator.comparingDouble(ScoredCandidate::score));
        return top.stream().map(this::toDto).toList();
    }

    private ScoredCandidate score(User viewer, Set<String> viewerSkills, Map<String, Double> viewerVector,
                                   Map<String, Double> idf, User candidate, EgoNetwork network, boolean isNetworkCandidate,
                                   Map<String, Set<String>> skillsByUserId, Map<String, String> docsByCandidateId) {
        int mutualConnections = isNetworkCandidate
                ? network.mutualConnectionCount(candidate.getId())
                : userService.getMutualConnections(viewer.getId(), candidate.getId(), 1).totalCount();

        Integer graphDistance = network.distanceByUserId().get(candidate.getId());

        Set<String> candidateSkills = skillsByUserId.getOrDefault(candidate.getId(), Set.of());
        Set<String> commonSkills = new HashSet<>(viewerSkills);
        commonSkills.retainAll(candidateSkills);
        double skillsScore = jaccard(viewerSkills, candidateSkills);

        boolean sameCollege = isPresent(viewer.getCollegeOrCompany()) && viewer.getCollegeOrCompany().equalsIgnoreCase(candidate.getCollegeOrCompany());
        boolean sameChapter = viewer.getChapterId() != null && viewer.getChapterId().equals(candidate.getChapterId());

        double lookingForOpenToScore = lookingForOpenToOverlap(viewer, candidate);

        double profileTextScore = CosineSimilarity.compute(viewerVector,
                TfIdfVectorizer.vectorize(docsByCandidateId.get(candidate.getId()), idf));

        double mutualScore = Math.min(mutualConnections / RecommendationWeights.MUTUAL_CONNECTIONS_NORMALIZATION_CAP, 1.0);
        double distanceScore = graphDistance == null ? RecommendationWeights.GRAPH_DISTANCE_FALLBACK_SCORE
                : graphDistance == 1 ? RecommendationWeights.GRAPH_DISTANCE_1_SCORE
                : RecommendationWeights.GRAPH_DISTANCE_2_SCORE;

        double score = mutualScore * RecommendationWeights.PEOPLE_WEIGHT_MUTUAL_CONNECTIONS
                + distanceScore * RecommendationWeights.PEOPLE_WEIGHT_GRAPH_DISTANCE
                + skillsScore * RecommendationWeights.PEOPLE_WEIGHT_COMMON_SKILLS
                + (sameCollege ? 1.0 : 0.0) * RecommendationWeights.PEOPLE_WEIGHT_SAME_COLLEGE
                + (sameChapter ? 1.0 : 0.0) * RecommendationWeights.PEOPLE_WEIGHT_SAME_CHAPTER
                + lookingForOpenToScore * RecommendationWeights.PEOPLE_WEIGHT_LOOKING_FOR_OPEN_TO
                + profileTextScore * RecommendationWeights.PEOPLE_WEIGHT_PROFILE_TEXT;

        List<String> reasons = buildReasons(mutualConnections, commonSkills, sameChapter, sameCollege, lookingForOpenToScore);
        return new ScoredCandidate(candidate, score, mutualConnections, commonSkills.stream().sorted().toList(), graphDistance, reasons);
    }

    private List<String> buildReasons(int mutualConnections, Set<String> commonSkills, boolean sameChapter,
                                       boolean sameCollege, double lookingForOpenToScore) {
        List<String> reasons = new ArrayList<>();
        if (mutualConnections > 0) {
            reasons.add(mutualConnections + " mutual connection" + (mutualConnections == 1 ? "" : "s"));
        }
        if (!commonSkills.isEmpty()) {
            reasons.add(commonSkills.size() + " common skill" + (commonSkills.size() == 1 ? "" : "s"));
        }
        if (sameChapter) reasons.add("Same chapter");
        if (sameCollege) reasons.add("Same college/company");
        if (lookingForOpenToScore > 0) reasons.add("Compatible looking-for / open-to interests");
        return reasons;
    }

    private double lookingForOpenToOverlap(User viewer, User candidate) {
        Set<LookingFor> viewerLookingFor = viewer.getLookingFor();
        Set<OpenTo> viewerOpenTo = viewer.getOpenTo();
        Set<LookingFor> candidateLookingFor = candidate.getLookingFor();
        Set<OpenTo> candidateOpenTo = candidate.getOpenTo();

        int overlap = 0;
        overlap += crossLabelOverlap(viewerLookingFor, candidateOpenTo);
        overlap += crossLabelOverlap(candidateLookingFor, viewerOpenTo);
        return Math.min(overlap / 2.0, 1.0);
    }

    private int crossLabelOverlap(Set<LookingFor> lookingFor, Set<OpenTo> openTo) {
        Set<String> lookingForLabels = new HashSet<>();
        for (LookingFor lf : lookingFor) lookingForLabels.add(lf.getLabel().toLowerCase());
        int count = 0;
        for (OpenTo ot : openTo) {
            if (lookingForLabels.contains(ot.getLabel().toLowerCase())) count++;
        }
        return count;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private Set<String> normalizedSkills(Set<String> skills) {
        Set<String> result = new HashSet<>();
        for (String skill : skills) result.add(skill.toLowerCase());
        return result;
    }

    private Map<String, Set<String>> bulkFetchSkills(Set<String> userIds) {
        Map<String, Set<String>> result = new HashMap<>();
        for (Object[] row : userRepository.findSkillsByUserIds(userIds)) {
            String userId = (String) row[0];
            String skill = ((String) row[1]).toLowerCase();
            result.computeIfAbsent(userId, k -> new HashSet<>()).add(skill);
        }
        return result;
    }

    private RecommendedUserDto toDto(ScoredCandidate candidate) {
        return new RecommendedUserDto(
                userMapper.toDto(candidate.user(), "NONE", false),
                Math.round(candidate.score() * 10000.0) / 10000.0,
                candidate.mutualConnections(),
                candidate.commonSkills(),
                candidate.graphDistance(),
                candidate.reasons()
        );
    }
}
