package com.nukkad.user.entity;

/**
 * The {@code user_social_links.platform} column is a genuine MySQL {@code ENUM}, defined in
 * V13/V17 migrations — it is a second, independently-maintained source of truth. Adding a
 * constant here without a matching {@code ALTER TABLE ... MODIFY COLUMN} migration compiles
 * fine but fails at insert time with a MySQL data-truncation error, not a compile error.
 */
public enum SocialPlatform {
    LINKEDIN, GITHUB, PORTFOLIO, TWITTER, KAGGLE, LEETCODE, BEHANCE, DRIBBBLE, MEDIUM,
    INSTAGRAM, YOUTUBE, STACKOVERFLOW, DEVTO, PRODUCTHUNT, HUGGINGFACE
}
