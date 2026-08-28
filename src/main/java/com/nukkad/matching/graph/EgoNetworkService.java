package com.nukkad.matching.graph;

import com.nukkad.user.entity.Connection;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * BFS over the accepted-connections graph, bounded to a viewer's 2-hop ego network — never loads the
 * whole graph. Two queries total regardless of network size: one for the viewer's direct connections
 * (the BFS frontier at distance 1), one bulk query for every edge touching that frontier (the entire
 * distance-2 expansion in a single round-trip). Complexity: O(deg(viewer) + sum of deg(each 1st-degree
 * connection)) — proportional to the size of the traversed subgraph, not the whole user base.
 */
@Service
public class EgoNetworkService {

    private final ConnectionRepository connectionRepository;
    private final UserBlockRepository userBlockRepository;

    public EgoNetworkService(ConnectionRepository connectionRepository, UserBlockRepository userBlockRepository) {
        this.connectionRepository = connectionRepository;
        this.userBlockRepository = userBlockRepository;
    }

    /**
     * @param distanceByUserId  2nd-degree candidates only (never includes the viewer's direct
     *                          connections — those are already-known relationships, not recommendations)
     * @param firstDegreeIds    the viewer's own direct connections — the BFS frontier, exposed so callers
     *                          can compute mutual-connection counts without another query
     * @param neighborsByUserId for each 2nd-degree candidate, which of the viewer's 1st-degree
     *                          connections they're directly connected to — this *is* the mutual-connections
     *                          set for that candidate, already available from the same bulk query
     */
    public record EgoNetwork(Map<String, Integer> distanceByUserId, Set<String> firstDegreeIds,
                              Map<String, Set<String>> neighborsByUserId) {

        public int mutualConnectionCount(String candidateId) {
            Set<String> candidateNeighbors = neighborsByUserId.getOrDefault(candidateId, Set.of());
            Set<String> intersection = new HashSet<>(firstDegreeIds);
            intersection.retainAll(candidateNeighbors);
            return intersection.size();
        }
    }

    @Transactional(readOnly = true)
    public EgoNetwork build(String viewerId, int maxDepth) {
        Set<String> degree1 = new LinkedHashSet<>();
        for (Connection c : connectionRepository.findAcceptedConnections(viewerId)) {
            degree1.add(otherParty(c, viewerId));
        }

        Map<String, Set<String>> neighbors = new HashMap<>();
        Map<String, Integer> distances = new HashMap<>();

        if (maxDepth >= 2 && !degree1.isEmpty()) {
            for (Connection edge : connectionRepository.findAcceptedInvolvingAny(degree1)) {
                neighbors.computeIfAbsent(edge.getUserAId(), k -> new HashSet<>()).add(edge.getUserBId());
                neighbors.computeIfAbsent(edge.getUserBId(), k -> new HashSet<>()).add(edge.getUserAId());
            }
            for (String firstDegreeMember : degree1) {
                for (String candidate : neighbors.getOrDefault(firstDegreeMember, Set.of())) {
                    if (candidate.equals(viewerId) || degree1.contains(candidate)) continue;
                    distances.putIfAbsent(candidate, 2);
                }
            }
        }

        Set<String> blocked = userBlockRepository.findBlockedEitherWayIds(viewerId);
        Set<String> alreadyRelated = new HashSet<>();
        for (Connection c : connectionRepository.findAllInvolving(viewerId)) {
            alreadyRelated.add(otherParty(c, viewerId));
        }
        distances.keySet().removeIf(id -> blocked.contains(id) || alreadyRelated.contains(id));

        return new EgoNetwork(distances, degree1, neighbors);
    }

    private String otherParty(Connection connection, String viewerId) {
        return connection.getUserAId().equals(viewerId) ? connection.getUserBId() : connection.getUserAId();
    }
}
