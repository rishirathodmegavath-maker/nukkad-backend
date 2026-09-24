package com.nukkad.feed.recommendation;

/**
 * Named constants for the personalized feed engine, in the same spirit as
 * {@code com.nukkad.matching.RecommendationWeights} — plain, documented, tunable-by-editing-a-
 * constant rather than a scattered magic number. This file starts with only the signal/decay
 * constants {@code UserTopicAffinityService} needs; the ranking-formula constants (candidate pool
 * size, per-factor weights, diversification/exploration caps) are added once the ranking engine
 * itself is built on top of this signal-capture layer.
 */
public final class FeedRankingWeights {

    private FeedRankingWeights() {}

    /** How much one LIKE moves a topic's affinity score. Save is weighted higher (a deliberate
     *  "keep this" action is a stronger signal than a lighter-weight reaction), matching the
     *  spec's own "Like: Very High, Save: Very High" — both high, save edges it out. */
    public static final double LIKE_WEIGHT = 0.30;

    public static final double SAVE_WEIGHT = 0.40;

    public static final double COMMENT_WEIGHT = 0.25;

    /** Sharing a post into a DM (the only "share" this app has) is a deliberate act of endorsing
     *  it to someone else — weighted alongside like/comment, not treated as lesser just because
     *  there's no public repost count to back it with. */
    public static final double SHARE_WEIGHT = 0.30;

    /** Opening a post is the weakest positive signal captured (no dwell-time tracking in v1 — see
     *  the personalized feed plan for why) — small on purpose so simply viewing many posts in a
     *  topic doesn't drown out a real like/save/comment. */
    public static final double OPEN_WEIGHT = 0.05;

    /** Hiding a post is a strong, deliberate negative — but it demotes only that specific post
     *  (see UserTopicAffinityService/PostHide), never the topic itself: liking other posts in the
     *  same topic still raises it. This constant governs the topic-affinity side of that signal;
     *  the post-specific side is the separate post_hides exclusion. */
    public static final double HIDE_WEIGHT = -1.0;

    /** Affinity decays at READ time (score * DECAY_FACTOR_PER_DAY ^ daysSinceUpdate), never via a
     *  scheduled job. 0.98/day ≈ a 34-day half-life — a month-old signal has faded to about half
     *  strength, recent behavior dominates, and nothing needs a cron sweep to stay meaningful. */
    public static final double AFFINITY_DECAY_FACTOR_PER_DAY = 0.98;

    // ---- Ranking formula (PersonalizedFeedService) — six already-[0,1]-normalized sub-scores,
    // combined as a weighted sum, same style as com.nukkad.matching.RecommendationWeights. Drops
    // the pasted spec's separate "historical_like_similarity" (folded into topicAffinityMatch,
    // which already IS the historical-like signal) and "engagement_prediction" (would need real
    // ML — faking it would be dishonest, not "smallest safe version"). ----

    public static final double WEIGHT_TOPIC_AFFINITY = 0.35;
    public static final double WEIGHT_TRENDING = 0.20;
    public static final double WEIGHT_FRESHNESS = 0.15;
    public static final double WEIGHT_CREATOR_AFFINITY = 0.15;
    public static final double WEIGHT_CONTENT_QUALITY = 0.10;
    public static final double WEIGHT_EXPLORATION = 0.05;

    /** How many days back the candidate pool looks for posts at all. */
    public static final int CANDIDATE_WINDOW_DAYS = 30;

    /** Hard ceiling on the in-memory candidate pool per request — bounded the same way
     *  DiscussionService.trendingPage's TRENDING_POOL_SIZE is, so ranking is always O(pool), never
     *  O(all posts). */
    public static final int CANDIDATE_POOL_MAX_SIZE = 300;

    /** Below this many candidates after the normal pool-gathering buckets, the pool is considered
     *  too thin (a true cold-start user with no follows and a quiet trending window) and a final,
     *  window-less fallback bucket tops it up — see PostCandidatePoolService. */
    public static final int MIN_POOL_SIZE = 20;

    /** How many of the viewer's top-scoring hashtags feed the affinity-matched candidate bucket. */
    public static final int TOP_AFFINITY_TAG_COUNT = 10;

    /** A raw (undecayed-cap) topic-affinity score is clamped to this before normalizing to [0,1] —
     *  a handful of compounding likes/saves on one topic reaches "fully matched" without needing
     *  an unbounded number of interactions first. */
    public static final double TOPIC_AFFINITY_SCORE_CAP = 2.0;

    /** Freshness half-life, in hours: a post this old is worth half of a brand-new one on this
     *  factor alone (still just one of six weighted terms, not the whole score). */
    public static final double FRESHNESS_HALF_LIFE_HOURS = 24.0;

    /** Diminishing-returns cap for "how many times has the viewer positively engaged with this
     *  author before" — same shape as RecommendationWeights' mutual-connections normalization. */
    public static final double CREATOR_PAST_ENGAGEMENT_CAP = 5.0;

    /** A candidate the viewer already liked/saved/opened within this window is demoted, not
     *  excluded — the same specific post moving down/out of the immediate stack per the product
     *  requirement, while liking it still raises the topic itself for everything else. */
    public static final int RECENT_INTERACTION_WINDOW_DAYS = 3;

    /** Multiplicative dampener applied to a recently-interacted-with candidate's final score. */
    public static final double RECENT_INTERACTION_PENALTY = 0.3;

    /** At most this many consecutive final-feed slots may share an author before diversification
     *  defers the next same-author candidate — a soft cap, never dropping a post if nothing else
     *  is available to fill the slot. */
    public static final int MAX_CONSECUTIVE_SAME_AUTHOR = 2;

    /** Roughly 1 in this many final slots is reserved for a "novel" candidate — one that doesn't
     *  overlap the viewer's current top affinity topics at all — so the feed can discover new
     *  interests instead of only ever narrowing toward existing ones. */
    public static final int EXPLORATION_SLOT_RATIO = 10;
}
