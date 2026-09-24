package com.nukkad.feed.recommendation;

import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.UserTopicAffinity;
import com.nukkad.feed.repository.PostHideRepository;
import com.nukkad.feed.repository.PostRepository;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserBlockRepository;
import com.nukkad.user.repository.UserFollowRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gathers a bounded, deduplicated candidate pool for the personalized feed — mirroring
 * {@code DiscussionService.trendingPage()}'s "bounded pool → score in memory" shape, not the
 * graph-BFS {@code com.nukkad.matching.graph.CandidatePoolService} (that one is built around the
 * People graph specifically; Posts have no ego-network concept to traverse). Never a full-table
 * scan: three bounded, indexed queries, unioned and deduplicated in Java, with a final unbounded
 * fallback only when the union is still thin.
 */
@Service
public class PostCandidatePoolService {

    /** Can never match a real UUID id — the "nothing to exclude" sentinel for a NOT IN clause. */
    private static final String NO_EXCLUSION_SENTINEL = "\u0000none";

    private final PostRepository postRepository;
    private final UserFollowRepository userFollowRepository;
    private final ConnectionRepository connectionRepository;
    private final UserBlockRepository userBlockRepository;
    private final PostHideRepository postHideRepository;
    private final Clock clock;

    public PostCandidatePoolService(PostRepository postRepository, UserFollowRepository userFollowRepository,
                                     ConnectionRepository connectionRepository, UserBlockRepository userBlockRepository,
                                     PostHideRepository postHideRepository, Clock clock) {
        this.postRepository = postRepository;
        this.userFollowRepository = userFollowRepository;
        this.connectionRepository = connectionRepository;
        this.userBlockRepository = userBlockRepository;
        this.postHideRepository = postHideRepository;
        this.clock = clock;
    }

    public List<Post> gather(String viewerId, Map<String, Double> affinityMap, Collection<String> clientExcludeIds) {
        Set<String> excludeIds = new HashSet<>(clientExcludeIds);
        excludeIds.addAll(postHideRepository.findAllPostIdsByUserId(viewerId));
        Collection<String> excludeOrSentinel = excludeIds.isEmpty() ? List.of(NO_EXCLUSION_SENTINEL) : excludeIds;

        Set<String> blockedIds = userBlockRepository.findBlockedEitherWayIds(viewerId);
        Instant since = clock.instant().minus(Duration.ofDays(FeedRankingWeights.CANDIDATE_WINDOW_DAYS));

        Map<String, Post> pool = new LinkedHashMap<>();

        Set<String> authorIds = followedAndConnectedAuthorIds(viewerId, blockedIds);
        if (!authorIds.isEmpty()) {
            for (Post p : postRepository.findRecentByAuthors(viewerId, authorIds, since, excludeOrSentinel,
                    PageRequest.of(0, FeedRankingWeights.CANDIDATE_POOL_MAX_SIZE))) {
                pool.put(p.getId(), p);
            }
        }

        List<String> topTags = topAffinityHashtags(affinityMap);
        if (!topTags.isEmpty()) {
            for (Post p : postRepository.findRecentByTags(viewerId, topTags, since, excludeOrSentinel,
                    PageRequest.of(0, FeedRankingWeights.CANDIDATE_POOL_MAX_SIZE))) {
                pool.putIfAbsent(p.getId(), p);
            }
        }

        for (Post p : postRepository.findRecentGeneral(viewerId, since, excludeOrSentinel,
                PageRequest.of(0, FeedRankingWeights.CANDIDATE_POOL_MAX_SIZE))) {
            pool.putIfAbsent(p.getId(), p);
        }

        List<Post> filtered = new ArrayList<>();
        for (Post p : pool.values()) {
            if (!blockedIds.contains(p.getAuthorId())) filtered.add(p);
        }

        // Thin-pool / true-cold-start fallback: no follows, no affinity, a quiet trending window —
        // widen with no recency window at all rather than ever returning an empty feed.
        if (filtered.size() < FeedRankingWeights.MIN_POOL_SIZE) {
            for (Post p : postRepository.findRecentGeneral(viewerId, Instant.EPOCH, excludeOrSentinel,
                    PageRequest.of(0, FeedRankingWeights.CANDIDATE_POOL_MAX_SIZE))) {
                if (!blockedIds.contains(p.getAuthorId()) && !pool.containsKey(p.getId())) {
                    filtered.add(p);
                }
            }
        }

        return filtered.size() > FeedRankingWeights.CANDIDATE_POOL_MAX_SIZE
                ? filtered.subList(0, FeedRankingWeights.CANDIDATE_POOL_MAX_SIZE)
                : filtered;
    }

    private Set<String> followedAndConnectedAuthorIds(String viewerId, Set<String> blockedIds) {
        Set<String> ids = new HashSet<>(userFollowRepository.findFolloweeIdsByFollowerId(viewerId));
        for (var connection : connectionRepository.findAcceptedConnections(viewerId)) {
            ids.add(connection.getUserAId().equals(viewerId) ? connection.getUserBId() : connection.getUserAId());
        }
        ids.removeAll(blockedIds);
        return ids;
    }

    /** The viewer's top positive-affinity hashtags — a topic the viewer actively dislikes (a
     *  negative score, e.g. from hiding posts on it) is never used to fetch MORE of it. */
    private List<String> topAffinityHashtags(Map<String, Double> affinityMap) {
        String prefix = UserTopicAffinity.TopicKind.HASHTAG + ":";
        return affinityMap.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix) && e.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(FeedRankingWeights.TOP_AFFINITY_TAG_COUNT)
                .map(e -> e.getKey().substring(prefix.length()))
                .toList();
    }
}
