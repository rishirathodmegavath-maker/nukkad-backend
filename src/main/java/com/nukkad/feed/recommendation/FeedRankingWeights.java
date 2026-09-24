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
}
