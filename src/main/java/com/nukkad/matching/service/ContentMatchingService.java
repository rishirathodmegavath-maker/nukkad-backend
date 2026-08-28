package com.nukkad.matching.service;

import com.nukkad.idea.dto.IdeaDto;
import com.nukkad.idea.entity.ContributionArea;
import com.nukkad.idea.entity.Idea;
import com.nukkad.idea.mapper.IdeaMapper;
import com.nukkad.idea.repository.IdeaInterestRepository;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.matching.RecommendationWeights;
import com.nukkad.matching.TopKSelector;
import com.nukkad.matching.dto.IdeaMatchDto;
import com.nukkad.matching.dto.OpportunityMatchDto;
import com.nukkad.matching.text.CosineSimilarity;
import com.nukkad.matching.text.TfIdfVectorizer;
import com.nukkad.opportunity.dto.OpportunityDto;
import com.nukkad.opportunity.entity.Opportunity;
import com.nukkad.opportunity.mapper.OpportunityMapper;
import com.nukkad.opportunity.repository.OpportunityApplicantRepository;
import com.nukkad.opportunity.repository.OpportunityInterestRepository;
import com.nukkad.opportunity.repository.OpportunityRepository;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserBlockRepository;
import com.nukkad.user.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content-based matching (TF-IDF + cosine) between a user's profile text and Ideas/Opportunities.
 * Reuses the exact same {@link TfIdfVectorizer}/{@link CosineSimilarity} engine as Part 1 and Part 3 —
 * one implementation, three call sites.
 */
@Service
public class ContentMatchingService {

    /** Ideas/opportunities are ranked over a recent window, not the whole table — see RecommendationWeights. */
    private static final int CANDIDATE_WINDOW_SIZE = 200;

    private final IdeaRepository ideaRepository;
    private final IdeaInterestRepository ideaInterestRepository;
    private final IdeaMapper ideaMapper;
    private final OpportunityRepository opportunityRepository;
    private final OpportunityApplicantRepository opportunityApplicantRepository;
    private final OpportunityInterestRepository opportunityInterestRepository;
    private final OpportunityMapper opportunityMapper;
    private final UserBlockRepository userBlockRepository;
    private final UserService userService;

    public ContentMatchingService(IdeaRepository ideaRepository, IdeaInterestRepository ideaInterestRepository,
                                   IdeaMapper ideaMapper, OpportunityRepository opportunityRepository,
                                   OpportunityApplicantRepository opportunityApplicantRepository,
                                   OpportunityInterestRepository opportunityInterestRepository,
                                   OpportunityMapper opportunityMapper, UserBlockRepository userBlockRepository,
                                   UserService userService) {
        this.ideaRepository = ideaRepository;
        this.ideaInterestRepository = ideaInterestRepository;
        this.ideaMapper = ideaMapper;
        this.opportunityRepository = opportunityRepository;
        this.opportunityApplicantRepository = opportunityApplicantRepository;
        this.opportunityInterestRepository = opportunityInterestRepository;
        this.opportunityMapper = opportunityMapper;
        this.userBlockRepository = userBlockRepository;
        this.userService = userService;
    }

    private record ScoredIdea(Idea idea, double score, List<String> reasons) {
    }

    private record ScoredOpportunity(Opportunity opportunity, double score, List<String> reasons) {
    }

    @Transactional(readOnly = true)
    public List<IdeaMatchDto> rankIdeasForUser(String viewerId, int limit) {
        User viewer = userService.getEntityOrThrow(viewerId);
        Set<String> blocked = userBlockRepository.findBlockedEitherWayIds(viewerId);
        Set<String> viewerSkills = lowercase(viewer.getSkills());

        List<Idea> candidates = ideaRepository
                .findAll(PageRequest.of(0, CANDIDATE_WINDOW_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .filter(idea -> !idea.getCreatorId().equals(viewerId))
                .filter(idea -> !idea.getTeamMemberIds().contains(viewerId))
                .filter(idea -> !blocked.contains(idea.getCreatorId()))
                .toList();
        if (candidates.isEmpty()) return List.of();

        String viewerDoc = ProfileTextBuilder.forUser(viewer);
        List<String> corpus = new ArrayList<>();
        corpus.add(viewerDoc);
        candidates.forEach(idea -> corpus.add(ProfileTextBuilder.forIdea(idea)));
        Map<String, Double> idf = TfIdfVectorizer.fit(corpus);
        Map<String, Double> viewerVector = TfIdfVectorizer.vectorize(viewerDoc, idf);

        List<ScoredIdea> scored = candidates.stream()
                .map(idea -> scoreIdea(idea, viewerVector, idf, viewerSkills))
                .toList();
        List<ScoredIdea> top = TopKSelector.topK(scored, limit, Comparator.comparingDouble(ScoredIdea::score));
        return top.stream().map(this::toIdeaMatchDto).toList();
    }

    @Transactional(readOnly = true)
    public IdeaMatchDto singleIdeaMatch(String viewerId, String ideaId) {
        User viewer = userService.getEntityOrThrow(viewerId);
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new com.nukkad.common.exception.ResourceNotFoundException("Idea not found: " + ideaId));

        String viewerDoc = ProfileTextBuilder.forUser(viewer);
        Map<String, Double> idf = TfIdfVectorizer.fit(List.of(viewerDoc, ProfileTextBuilder.forIdea(idea)));
        Map<String, Double> viewerVector = TfIdfVectorizer.vectorize(viewerDoc, idf);

        ScoredIdea scored = scoreIdea(idea, viewerVector, idf, lowercase(viewer.getSkills()));
        return toIdeaMatchDto(scored);
    }

    private ScoredIdea scoreIdea(Idea idea, Map<String, Double> viewerVector, Map<String, Double> idf, Set<String> viewerSkills) {
        double score = CosineSimilarity.compute(viewerVector, TfIdfVectorizer.vectorize(ProfileTextBuilder.forIdea(idea), idf));

        List<String> reasons = new ArrayList<>();
        Set<String> matchedTags = new HashSet<>(lowercase(idea.getTags()));
        matchedTags.retainAll(viewerSkills);
        if (!matchedTags.isEmpty()) {
            reasons.add("Matches your skills: " + String.join(", ", matchedTags));
        }
        List<String> matchedHelpNeeded = idea.getHelpNeeded().stream()
                .map(ContributionArea::getLabel)
                .filter(label -> viewerSkills.stream().anyMatch(skill -> fuzzyContains(label, skill)))
                .toList();
        if (!matchedHelpNeeded.isEmpty()) {
            reasons.add("Idea needs help with: " + String.join(", ", matchedHelpNeeded));
        }
        return new ScoredIdea(idea, score, reasons);
    }

    @Transactional(readOnly = true)
    public List<OpportunityMatchDto> rankOpportunitiesForUser(String viewerId, int limit) {
        User viewer = userService.getEntityOrThrow(viewerId);
        Set<String> blocked = userBlockRepository.findBlockedEitherWayIds(viewerId);
        Set<String> viewerSkills = lowercase(viewer.getSkills());

        Set<String> alreadyInteracted = new HashSet<>();
        opportunityApplicantRepository.findByUserId(viewerId).forEach(a -> alreadyInteracted.add(a.getOpportunityId()));
        opportunityInterestRepository.findByUserId(viewerId).forEach(i -> alreadyInteracted.add(i.getOpportunityId()));

        List<Opportunity> candidates = opportunityRepository
                .findAll(PageRequest.of(0, CANDIDATE_WINDOW_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .filter(opp -> !opp.getPostedByUserId().equals(viewerId))
                .filter(opp -> !alreadyInteracted.contains(opp.getId()))
                .filter(opp -> !blocked.contains(opp.getPostedByUserId()))
                .toList();
        if (candidates.isEmpty()) return List.of();

        String viewerDoc = ProfileTextBuilder.forUser(viewer);
        List<String> corpus = new ArrayList<>();
        corpus.add(viewerDoc);
        candidates.forEach(opp -> corpus.add(ProfileTextBuilder.forOpportunity(opp)));
        Map<String, Double> idf = TfIdfVectorizer.fit(corpus);
        Map<String, Double> viewerVector = TfIdfVectorizer.vectorize(viewerDoc, idf);

        List<ScoredOpportunity> scored = candidates.stream()
                .map(opp -> scoreOpportunity(opp, viewerVector, idf, viewerSkills, viewer))
                .toList();
        List<ScoredOpportunity> top = TopKSelector.topK(scored, limit, Comparator.comparingDouble(ScoredOpportunity::score));
        return top.stream().map(this::toOpportunityMatchDto).toList();
    }

    @Transactional(readOnly = true)
    public OpportunityMatchDto singleOpportunityMatch(String viewerId, String opportunityId) {
        User viewer = userService.getEntityOrThrow(viewerId);
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new com.nukkad.common.exception.ResourceNotFoundException("Opportunity not found: " + opportunityId));

        String viewerDoc = ProfileTextBuilder.forUser(viewer);
        Map<String, Double> idf = TfIdfVectorizer.fit(List.of(viewerDoc, ProfileTextBuilder.forOpportunity(opportunity)));
        Map<String, Double> viewerVector = TfIdfVectorizer.vectorize(viewerDoc, idf);

        ScoredOpportunity scored = scoreOpportunity(opportunity, viewerVector, idf, lowercase(viewer.getSkills()), viewer);
        return toOpportunityMatchDto(scored);
    }

    private ScoredOpportunity scoreOpportunity(Opportunity opportunity, Map<String, Double> viewerVector,
                                                Map<String, Double> idf, Set<String> viewerSkills, User viewer) {
        double score = CosineSimilarity.compute(viewerVector, TfIdfVectorizer.vectorize(ProfileTextBuilder.forOpportunity(opportunity), idf));

        List<String> reasons = new ArrayList<>();
        List<String> matchedRequirements = opportunity.getRequirements().stream()
                .filter(req -> viewerSkills.stream().anyMatch(skill -> fuzzyContains(req, skill)))
                .toList();
        if (!matchedRequirements.isEmpty()) {
            reasons.add("Matches your skills: " + String.join(", ", matchedRequirements));
        }
        boolean typeAligns = viewer.getLookingFor().stream()
                .anyMatch(lf -> lf.getLabel().equalsIgnoreCase(opportunity.getType().getLabel()));
        if (typeAligns) {
            reasons.add("Matches what you're looking for: " + opportunity.getType().getLabel());
        }
        return new ScoredOpportunity(opportunity, score, reasons);
    }

    private boolean fuzzyContains(String haystack, String needle) {
        String h = haystack.toLowerCase();
        String n = needle.toLowerCase();
        return h.contains(n) || n.contains(h);
    }

    private Set<String> lowercase(Iterable<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) result.add(value.toLowerCase());
        return result;
    }

    private IdeaMatchDto toIdeaMatchDto(ScoredIdea scored) {
        IdeaDto dto = ideaMapper.toDto(scored.idea(), (int) ideaInterestRepository.countByIdeaId(scored.idea().getId()));
        return new IdeaMatchDto(dto, round(scored.score()), RecommendationWeights.toMatchLabel(scored.score()), scored.reasons());
    }

    private OpportunityMatchDto toOpportunityMatchDto(ScoredOpportunity scored) {
        Opportunity opp = scored.opportunity();
        // hasApplied/hasExpressedInterest/applicationStatus are always false/null here by construction —
        // candidates the viewer already applied to or expressed interest in are filtered out of the
        // ranking before this point.
        OpportunityDto dto = opportunityMapper.toDto(opp, false, false, null,
                (int) opportunityApplicantRepository.countByOpportunityId(opp.getId()),
                (int) opportunityInterestRepository.countByOpportunityId(opp.getId()));
        return new OpportunityMatchDto(dto, round(scored.score()), RecommendationWeights.toMatchLabel(scored.score()), scored.reasons());
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
