package com.nukkad.matching.graph;

import com.nukkad.matching.RecommendationWeights;
import com.nukkad.matching.graph.EgoNetworkService.EgoNetwork;
import com.nukkad.user.entity.Connection;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserBlockRepository;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.repository.UserSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared candidate-gathering for both "People You May Know" ({@link com.nukkad.matching.service.PeopleRecommendationService})
 * and co-founder compatibility ({@link com.nukkad.matching.service.CompatibilityService}) — the same
 * 2-hop ego network, topped up with a fallback pool when the network is too small, with two different
 * scorers layered on top. Kept as its own service so the graph-traversal/exclusion logic exists exactly once.
 */
@Service
public class CandidatePoolService {

    public record CandidatePool(EgoNetwork network, Set<String> networkCandidateIds, List<User> allCandidates) {
    }

    private final EgoNetworkService egoNetworkService;
    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    public CandidatePoolService(EgoNetworkService egoNetworkService, ConnectionRepository connectionRepository,
                                 UserRepository userRepository, UserBlockRepository userBlockRepository) {
        this.egoNetworkService = egoNetworkService;
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
        this.userBlockRepository = userBlockRepository;
    }

    @Transactional(readOnly = true)
    public CandidatePool gather(User viewer, int limit) {
        EgoNetwork network = egoNetworkService.build(viewer.getId(), RecommendationWeights.MAX_GRAPH_DEPTH);

        Set<String> networkCandidateIds = network.distanceByUserId().keySet();
        List<User> networkCandidates = networkCandidateIds.isEmpty() ? List.of() : userRepository.findAllById(networkCandidateIds);

        List<User> fallbackCandidates = networkCandidates.size() >= limit
                ? List.of()
                : fetchFallbackCandidates(viewer, network, networkCandidateIds, limit - networkCandidates.size());

        List<User> allCandidates = new ArrayList<>(networkCandidates.size() + fallbackCandidates.size());
        allCandidates.addAll(networkCandidates);
        allCandidates.addAll(fallbackCandidates);
        return new CandidatePool(network, networkCandidateIds, allCandidates);
    }

    /**
     * Tops up the candidate pool when the 2-hop ego network is smaller than {@code limit} (e.g. a new
     * user with few connections) — reuses the exact chapter/exclude-blocked candidate specification
     * {@code UserService.listSuggested} already builds, rather than duplicating it.
     */
    private List<User> fetchFallbackCandidates(User viewer, EgoNetwork network, Set<String> networkCandidateIds, int need) {
        Set<String> exclude = new HashSet<>(networkCandidateIds);
        exclude.add(viewer.getId());
        exclude.addAll(network.firstDegreeIds());
        exclude.addAll(userBlockRepository.findBlockedEitherWayIds(viewer.getId()));
        for (Connection c : connectionRepository.findAllInvolving(viewer.getId())) {
            exclude.add(c.getUserAId().equals(viewer.getId()) ? c.getUserBId() : c.getUserAId());
        }

        Specification<User> spec = UserSpecifications.combine(
                UserSpecifications.excludeIds(exclude),
                viewer.getChapterId() != null ? UserSpecifications.chapterId(viewer.getChapterId()) : null
        );
        List<User> candidates = new ArrayList<>(userRepository.findAll(spec, PageRequest.of(0, need, Sort.by(Sort.Direction.DESC, "connectionsCount"))).getContent());

        if (candidates.size() < need && viewer.getChapterId() != null) {
            Specification<User> widened = UserSpecifications.excludeIds(exclude);
            List<User> more = userRepository.findAll(widened, PageRequest.of(0, need)).getContent();
            for (User u : more) {
                if (candidates.size() >= need) break;
                if (candidates.stream().noneMatch(c -> c.getId().equals(u.getId()))) candidates.add(u);
            }
        }
        return candidates;
    }
}
