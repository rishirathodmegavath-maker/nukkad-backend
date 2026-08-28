package com.nukkad.matching;

/**
 * Named weighting/threshold constants for every scoring context in the matching engine — kept in one
 * place instead of scattered magic numbers, so tuning is a one-line change with a documented reason.
 */
public final class RecommendationWeights {

    private RecommendationWeights() {
    }

    // ---- Part 1: People You May Know ----
    // Chosen from what Nukkad's data model actually supports (confirmed via inspection), not copied
    // from an example. Mutual connections dominates — it's the richest, most reliable signal available
    // and matches how real PYMK systems weight social proximity. Sums to 1.0.
    public static final double PEOPLE_WEIGHT_MUTUAL_CONNECTIONS = 0.35;
    public static final double PEOPLE_WEIGHT_GRAPH_DISTANCE = 0.15;
    public static final double PEOPLE_WEIGHT_COMMON_SKILLS = 0.20;
    public static final double PEOPLE_WEIGHT_SAME_COLLEGE = 0.10;
    public static final double PEOPLE_WEIGHT_SAME_CHAPTER = 0.10;
    public static final double PEOPLE_WEIGHT_LOOKING_FOR_OPEN_TO = 0.05;
    public static final double PEOPLE_WEIGHT_PROFILE_TEXT = 0.05;

    /** BFS depth for ego-network expansion — 2 hops (LinkedIn-style "2nd degree") balances recall against candidate-pool size. */
    public static final int MAX_GRAPH_DEPTH = 2;
    /** Diminishing-returns cap: a candidate with 10+ mutual connections is treated the same as one with exactly 10. */
    public static final double MUTUAL_CONNECTIONS_NORMALIZATION_CAP = 10.0;
    public static final double GRAPH_DISTANCE_1_SCORE = 1.0;
    public static final double GRAPH_DISTANCE_2_SCORE = 0.5;
    public static final double GRAPH_DISTANCE_FALLBACK_SCORE = 0.1;

    // ---- Part 3: Co-founder / collaborator compatibility ----
    // Complementarity is weighted above raw similarity on purpose — two founders who are too similar
    // (same skills, same gaps) are a weaker co-founder match than two who fill each other's gaps.
    public static final double COFOUNDER_WEIGHT_COMPLEMENTARITY = 0.40;
    public static final double COFOUNDER_WEIGHT_PROFILE_SIMILARITY = 0.25;
    public static final double COFOUNDER_WEIGHT_MUTUAL_CONNECTIONS = 0.20;
    public static final double COFOUNDER_WEIGHT_LOOKING_FOR_OPEN_TO = 0.15;

    /**
     * Minimum blended score a co-founder candidate must clear to be shown at all. Below this, a match
     * is more noise than signal (e.g. a distant fallback candidate with no real complementarity or
     * connection) — better to show fewer, meaningful matches than pad out to a fixed count. Chosen from
     * live-tested score distributions: genuinely complementary founders scored 0.40+, low-signal
     * candidates scored under 0.10.
     */
    public static final double COFOUNDER_MIN_QUALITY_SCORE = 0.20;

    /**
     * The quality filter above only removes candidates — it doesn't add any back. If the candidate pool
     * itself is sized to exactly {@code limit}, filtering can leave fewer results than requested even
     * when more genuinely qualifying candidates exist just outside that small pool (observed live: at
     * limit=6 only 4 of the 12 real qualifying matches were even considered). So the pool requested from
     * {@code CandidatePoolService} is widened by this multiplier — capped at
     * {@link #COFOUNDER_CANDIDATE_POOL_MAX} to keep scoring/TF-IDF cost bounded — giving the filter
     * enough candidates to work with before {@link TopKSelector} trims back down to exactly {@code limit}.
     */
    public static final int COFOUNDER_CANDIDATE_POOL_MULTIPLIER = 3;
    public static final int COFOUNDER_CANDIDATE_POOL_MAX = 60;

    // ---- Part 2: content-match wording thresholds (never show a raw score in the UI) ----
    public static final double MATCH_LABEL_STRONG_THRESHOLD = 0.5;
    public static final double MATCH_LABEL_GOOD_THRESHOLD = 0.25;
    // Below MATCH_LABEL_GOOD_THRESHOLD is labeled "Potential match".

    public static String toMatchLabel(double score) {
        if (score >= MATCH_LABEL_STRONG_THRESHOLD) return "Strong match";
        if (score >= MATCH_LABEL_GOOD_THRESHOLD) return "Good match";
        return "Potential match";
    }
}
