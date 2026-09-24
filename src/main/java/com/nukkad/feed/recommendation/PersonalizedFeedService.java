package com.nukkad.feed.recommendation;

import com.nukkad.feed.dto.PersonalizedFeedResultDto;
import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.UserTopicAffinity;
import com.nukkad.feed.repository.PostHashtagRepository;
import com.nukkad.feed.repository.PostInteractionRepository;
import com.nukkad.feed.service.FeedService;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserFollowRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates the personalized feed: gather a bounded candidate pool, score it with six
 * already-[0,1]-normalized sub-scores (see {@link FeedRankingWeights}), diversify, and return
 * only the requested slice. Every per-candidate lookup (affinity, trending, creator engagement,
 * recent interactions) is batch-loaded once for the whole pool before scoring — never
 * per-candidate — the one easy-to-get-wrong N+1 risk in this design.
 */
@Service
public class PersonalizedFeedService {

    /** Matches the app's existing Trending Topics convention (FeedService.trendingTopics'
     *  default, DiscussionService.TRENDING_WINDOW_DAYS) — this internal signal stays consistent
     *  with what's already user-visible elsewhere. */
    private static final int TRENDING_WINDOW_DAYS = 14;
    private static final int TRENDING_TAGS_CONSIDERED = 50;

    private final PostCandidatePoolService candidatePoolService;
    private final UserTopicAffinityService affinityService;
    private final PostHashtagRepository postHashtagRepository;
    private final PostInteractionRepository postInteractionRepository;
    private final UserFollowRepository userFollowRepository;
    private final ConnectionRepository connectionRepository;
    private final FeedService feedService;
    private final Clock clock;

    public PersonalizedFeedService(PostCandidatePoolService candidatePoolService, UserTopicAffinityService affinityService,
                                    PostHashtagRepository postHashtagRepository, PostInteractionRepository postInteractionRepository,
                                    UserFollowRepository userFollowRepository, ConnectionRepository connectionRepository,
                                    FeedService feedService, Clock clock) {
        this.candidatePoolService = candidatePoolService;
        this.affinityService = affinityService;
        this.postHashtagRepository = postHashtagRepository;
        this.postInteractionRepository = postInteractionRepository;
        this.userFollowRepository = userFollowRepository;
        this.connectionRepository = connectionRepository;
        this.feedService = feedService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PersonalizedFeedResultDto list(String viewerId, int size, List<String> excludeIds) {
        Map<String, Double> affinityMap = affinityService.affinityMap(viewerId);
        List<Post> pool = candidatePoolService.gather(viewerId, affinityMap, excludeIds);
        if (pool.isEmpty()) return new PersonalizedFeedResultDto(List.of(), false);

        List<String> poolIds = pool.stream().map(Post::getId).toList();
        Instant now = clock.instant();

        Map<String, List<String>> tagsByPost = batchTags(poolIds);
        Map<String, Long> trendingCounts = trendingCountsMap(viewerId, now.minus(Duration.ofDays(TRENDING_WINDOW_DAYS)));
        long maxTrendingCount = trendingCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);

        Set<String> followedIds = new HashSet<>(userFollowRepository.findFolloweeIdsByFollowerId(viewerId));
        Set<String> connectedIds = connectionRepository.findAcceptedConnections(viewerId).stream()
                .map(c -> c.getUserAId().equals(viewerId) ? c.getUserBId() : c.getUserAId())
                .collect(Collectors.toSet());
        Set<String> authorIds = pool.stream().map(Post::getAuthorId).collect(Collectors.toSet());
        Map<String, Long> positiveByAuthor = batchPositiveInteractionsByAuthor(viewerId, authorIds);

        Instant recentSince = now.minus(Duration.ofDays(FeedRankingWeights.RECENT_INTERACTION_WINDOW_DAYS));
        Set<String> recentlyInteractedIds = postInteractionRepository.findRecentlyInteractedPostIds(viewerId, poolIds, recentSince);

        Map<String, Double> velocityByPost = new HashMap<>();
        double maxVelocity = 0;
        for (Post p : pool) {
            double hours = Math.max(1.0, Duration.between(p.getCreatedAt(), now).toMinutes() / 60.0);
            double velocity = (p.getLikesCount() + p.getCommentsCount()) / hours;
            velocityByPost.put(p.getId(), velocity);
            maxVelocity = Math.max(maxVelocity, velocity);
        }

        Set<String> topAffinityKeys = topAffinityTopicKeys(affinityMap);

        List<ScoredPost> scored = new ArrayList<>();
        for (Post p : pool) {
            List<String> tags = tagsByPost.getOrDefault(p.getId(), List.of());

            double topicAffinity = topicAffinityScore(affinityMap, tags, p.getType());
            double trending = maxTrendingCount == 0 ? 0
                    : tags.stream().mapToLong(t -> trendingCounts.getOrDefault(t, 0L)).max().orElse(0) / (double) maxTrendingCount;
            double freshnessHours = Math.max(0, Duration.between(p.getCreatedAt(), now).toMinutes() / 60.0);
            double freshness = Math.exp(-freshnessHours / FeedRankingWeights.FRESHNESS_HALF_LIFE_HOURS);
            double creatorAffinity = creatorAffinityScore(p.getAuthorId(), followedIds, connectedIds, positiveByAuthor);
            double contentQuality = maxVelocity == 0 ? 0 : velocityByPost.get(p.getId()) / maxVelocity;
            boolean novel = !overlapsTopAffinities(tags, p.getType(), topAffinityKeys);
            double exploration = novel ? 1.0 : 0.0;

            double base = FeedRankingWeights.WEIGHT_TOPIC_AFFINITY * topicAffinity
                    + FeedRankingWeights.WEIGHT_TRENDING * trending
                    + FeedRankingWeights.WEIGHT_FRESHNESS * freshness
                    + FeedRankingWeights.WEIGHT_CREATOR_AFFINITY * creatorAffinity
                    + FeedRankingWeights.WEIGHT_CONTENT_QUALITY * contentQuality
                    + FeedRankingWeights.WEIGHT_EXPLORATION * exploration;

            // A soft demotion of this specific post — recently liking/saving/opening it should not
            // remove AI content in general, just stop this exact post from immediately resurfacing.
            double finalScore = recentlyInteractedIds.contains(p.getId()) ? base * FeedRankingWeights.RECENT_INTERACTION_PENALTY : base;
            scored.add(new ScoredPost(p, finalScore, novel));
        }

        List<Post> selected = selectDiversified(scored, size);
        boolean hasMore = pool.size() > selected.size();

        return new PersonalizedFeedResultDto(feedService.toDtoList(selected, viewerId), hasMore);
    }

    private double topicAffinityScore(Map<String, Double> affinityMap, List<String> tags, Post.Type type) {
        double raw = 0;
        for (String tag : tags) {
            raw = Math.max(raw, affinityMap.getOrDefault(UserTopicAffinity.TopicKind.HASHTAG + ":" + tag, 0.0));
        }
        raw = Math.max(raw, affinityMap.getOrDefault(UserTopicAffinity.TopicKind.POST_TYPE + ":" + type.name(), 0.0));
        double clamped = Math.max(0, Math.min(raw, FeedRankingWeights.TOPIC_AFFINITY_SCORE_CAP));
        return clamped / FeedRankingWeights.TOPIC_AFFINITY_SCORE_CAP;
    }

    private double creatorAffinityScore(String authorId, Set<String> followedIds, Set<String> connectedIds,
                                         Map<String, Long> positiveByAuthor) {
        double relationshipBoost = (followedIds.contains(authorId) || connectedIds.contains(authorId)) ? 1.0 : 0.0;
        double pastEngagement = Math.min(positiveByAuthor.getOrDefault(authorId, 0L) / FeedRankingWeights.CREATOR_PAST_ENGAGEMENT_CAP, 1.0);
        return 0.5 * relationshipBoost + 0.5 * pastEngagement;
    }

    /** The viewer's current top affinity topics (any positive-score kind, hashtag or type) — used
     *  only to decide what counts as "novel" for the exploration slot, never to filter anything out. */
    private Set<String> topAffinityTopicKeys(Map<String, Double> affinityMap) {
        return affinityMap.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(FeedRankingWeights.TOP_AFFINITY_TAG_COUNT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private boolean overlapsTopAffinities(List<String> tags, Post.Type type, Set<String> topAffinityKeys) {
        if (topAffinityKeys.isEmpty()) return false;
        for (String tag : tags) {
            if (topAffinityKeys.contains(UserTopicAffinity.TopicKind.HASHTAG + ":" + tag)) return true;
        }
        return topAffinityKeys.contains(UserTopicAffinity.TopicKind.POST_TYPE + ":" + type.name());
    }

    /**
     * Sorts by score, reserves ~1-in-{@code EXPLORATION_SLOT_RATIO} final slots for a novel
     * candidate, and diversifies the rest against {@code MAX_CONSECUTIVE_SAME_AUTHOR} — but never
     * drops a post to satisfy either rule if nothing else is available: every pass that skips a
     * candidate is followed by a backfill pass that can take it anyway.
     */
    private List<Post> selectDiversified(List<ScoredPost> scored, int size) {
        List<ScoredPost> byScore = new ArrayList<>(scored);
        byScore.sort(Comparator.comparingDouble(ScoredPost::score).reversed());

        boolean anyNovel = byScore.stream().anyMatch(ScoredPost::novel);
        int explorationSlots = (anyNovel && size > 0)
                ? Math.min(size, Math.max(1, size / FeedRankingWeights.EXPLORATION_SLOT_RATIO))
                : 0;
        int mainTarget = size - explorationSlots;

        List<Post> result = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();
        Deque<String> recentAuthors = new ArrayDeque<>();
        List<ScoredPost> deferred = new ArrayList<>();

        for (ScoredPost sp : byScore) {
            if (result.size() >= mainTarget) break;
            if (violatesConsecutiveCap(recentAuthors, sp.post().getAuthorId())) {
                deferred.add(sp);
                continue;
            }
            result.add(sp.post());
            selectedIds.add(sp.post().getId());
            pushAuthor(recentAuthors, sp.post().getAuthorId());
        }

        for (ScoredPost sp : byScore) {
            if (result.size() >= size) break;
            if (!sp.novel() || selectedIds.contains(sp.post().getId())) continue;
            if (violatesConsecutiveCap(recentAuthors, sp.post().getAuthorId())) continue;
            result.add(sp.post());
            selectedIds.add(sp.post().getId());
            pushAuthor(recentAuthors, sp.post().getAuthorId());
        }

        for (ScoredPost sp : deferred) {
            if (result.size() >= size) break;
            if (selectedIds.contains(sp.post().getId())) continue;
            result.add(sp.post());
            selectedIds.add(sp.post().getId());
        }
        for (ScoredPost sp : byScore) {
            if (result.size() >= size) break;
            if (selectedIds.contains(sp.post().getId())) continue;
            result.add(sp.post());
            selectedIds.add(sp.post().getId());
        }

        return result;
    }

    private boolean violatesConsecutiveCap(Deque<String> recentAuthors, String authorId) {
        if (recentAuthors.size() < FeedRankingWeights.MAX_CONSECUTIVE_SAME_AUTHOR) return false;
        return recentAuthors.stream().allMatch(a -> a.equals(authorId));
    }

    private void pushAuthor(Deque<String> recentAuthors, String authorId) {
        recentAuthors.addLast(authorId);
        if (recentAuthors.size() > FeedRankingWeights.MAX_CONSECUTIVE_SAME_AUTHOR) recentAuthors.removeFirst();
    }

    private Map<String, List<String>> batchTags(Collection<String> postIds) {
        Map<String, List<String>> tags = new HashMap<>();
        for (Object[] row : postHashtagRepository.findTagsByPostIds(postIds)) {
            tags.computeIfAbsent((String) row[0], k -> new ArrayList<>()).add((String) row[1]);
        }
        return tags;
    }

    private Map<String, Long> batchPositiveInteractionsByAuthor(String viewerId, Collection<String> authorIds) {
        if (authorIds.isEmpty()) return Map.of();
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : postInteractionRepository.countPositiveInteractionsByAuthor(viewerId, authorIds)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Map<String, Long> trendingCountsMap(String viewerId, Instant since) {
        Map<String, Long> counts = new HashMap<>();
        for (PostHashtagRepository.TagCount row : postHashtagRepository.findTrending(viewerId, since, PageRequest.of(0, TRENDING_TAGS_CONSIDERED))) {
            counts.put(row.getTag(), row.getPostCount());
        }
        return counts;
    }

    private record ScoredPost(Post post, double score, boolean novel) {}
}
