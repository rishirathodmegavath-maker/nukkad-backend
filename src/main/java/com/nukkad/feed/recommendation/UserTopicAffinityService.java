package com.nukkad.feed.recommendation;

import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.PostInteraction;
import com.nukkad.feed.entity.UserTopicAffinity;
import com.nukkad.feed.repository.UserTopicAffinityRepository;
import com.nukkad.feed.service.Hashtags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Learns which hashtags/post-types a user's behavior favors, from real signals only — never just
 * the most recent one. Every score read (here, or later by the ranking engine) goes through
 * {@link #decay}, computed at read time from a plain stored value, so nothing needs a scheduled
 * job to stay meaningful as time passes.
 */
@Service
public class UserTopicAffinityService {

    private final UserTopicAffinityRepository repository;
    private final Clock clock;

    public UserTopicAffinityService(UserTopicAffinityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Call after an interaction already succeeded (like/save/comment/share/hide/open) — bumps
     *  every hashtag in the post's text, and the post's type, by that interaction's weight. A
     *  weight of 0 (an interaction type with no configured weight) is a no-op, not an error. */
    @Transactional
    public void recordSignal(String userId, Post post, PostInteraction.Type interactionType) {
        double weight = weightFor(interactionType);
        if (weight == 0) return;

        for (String tag : Hashtags.extract(post.getContent())) {
            bump(userId, UserTopicAffinity.TopicKind.HASHTAG, tag, weight);
        }
        bump(userId, UserTopicAffinity.TopicKind.POST_TYPE, post.getType().name(), weight);
    }

    private double weightFor(PostInteraction.Type type) {
        return switch (type) {
            case LIKE -> FeedRankingWeights.LIKE_WEIGHT;
            case UNLIKE -> -FeedRankingWeights.LIKE_WEIGHT;
            case SAVE -> FeedRankingWeights.SAVE_WEIGHT;
            case UNSAVE -> -FeedRankingWeights.SAVE_WEIGHT;
            case COMMENT -> FeedRankingWeights.COMMENT_WEIGHT;
            case SHARE -> FeedRankingWeights.SHARE_WEIGHT;
            case OPEN -> FeedRankingWeights.OPEN_WEIGHT;
            case HIDE -> FeedRankingWeights.HIDE_WEIGHT;
            case UNHIDE -> -FeedRankingWeights.HIDE_WEIGHT;
        };
    }

    /** A user's own affinity row is never contended by another user, so a plain find-then-save
     *  (unlike the atomic UPDATE posts.likes_count needs to avoid cross-user deadlocks) is safe —
     *  the worst case under a same-user double-click race is a rare, harmless lost update to a
     *  soft ranking signal, not a correctness or money issue. */
    private void bump(String userId, UserTopicAffinity.TopicKind kind, String key, double delta) {
        Instant now = clock.instant();
        UserTopicAffinity affinity = repository.findByUserIdAndTopicKindAndTopicKey(userId, kind, key)
                .orElseGet(() -> UserTopicAffinity.builder().userId(userId).topicKind(kind).topicKey(key).score(0).updatedAt(now).build());
        double decayed = decay(affinity.getScore(), affinity.getUpdatedAt(), now);
        affinity.setScore(decayed + delta);
        affinity.setUpdatedAt(now);
        repository.save(affinity);
    }

    /** Decays a stored score to what it's worth right now — never negative-days (a clock that
     *  hasn't moved, or a brand-new row, decays by zero). */
    double decay(double score, Instant updatedAt, Instant now) {
        if (updatedAt == null) return score;
        long days = Duration.between(updatedAt, now).toDays();
        if (days <= 0) return score;
        return score * Math.pow(FeedRankingWeights.AFFINITY_DECAY_FACTOR_PER_DAY, days);
    }

    /** Every topic this user has affinity for, decayed to right now, keyed {@code "KIND:key"}
     *  (e.g. {@code "HASHTAG:ai"}, {@code "POST_TYPE:announcement"}) — the shape the ranking
     *  engine consumes. Empty for a cold-start user, never null/missing entries. */
    @Transactional(readOnly = true)
    public Map<String, Double> affinityMap(String userId) {
        Instant now = clock.instant();
        return repository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        a -> a.getTopicKind() + ":" + a.getTopicKey(),
                        a -> decay(a.getScore(), a.getUpdatedAt(), now)));
    }
}
