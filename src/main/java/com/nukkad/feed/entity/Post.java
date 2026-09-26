package com.nukkad.feed.entity;

import com.nukkad.common.publishing.PublisherIdentity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    /** text / startup_update / idea / opportunity / event exist from the start (the last four were meant to be posts
     *  the system writes about an entity; today they are simply kinds a member can pick as well). discussion through
     *  product_launch are the other kinds a member picks in the Create a Post dialog. */
    public enum Type {
        text, startup_update, idea, opportunity, event,
        discussion, build_update, question, milestone,
        feedback, cofounder, announcement, resource, hiring, fundraising, product_launch
    }

    /** Who may read a post: everyone, or only its author and the author's accepted connections. */
    public enum Visibility { PUBLIC, CONNECTIONS }

    /**
     * A curated category, meaningful only for {@code type = discussion} (null for every other kind).
     * Code-curated on purpose, same idea as {@code ResourceCategory} ("adding a topic is a code
     * change") — a fixed, small, labelled list reads better for "Popular Topics" than the free-text,
     * uncurated hashtags anyone can type (see PostHashtag/Hashtags), which stay a separate concept.
     * Stored with @Enumerated(STRING) like {@link Type}/{@link Visibility} above, so adding a new
     * topic later is a plain code change — no native-ENUM column to widen.
     */
    public enum Topic {
        AI_TECHNOLOGY("AI & Technology"), PRODUCT("Product"), GROWTH("Growth"), FUNDRAISING("Fundraising"),
        COFOUNDERS("Co-founders"), MARKETING("Marketing"), HIRING("Hiring"), TOOLS_RESOURCES("Tools & Resources"),
        STARTUPS("Startups"), GENERAL("General");

        private final String label;

        Topic(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "author_id", nullable = false, columnDefinition = "CHAR(36)")
    private String authorId;

    /** True only for a post an admin published from the admin panel without attributing it to a
     *  member — {@code authorId} is then the admin's own account, but the public-facing author
     *  shown for it is the BuildAdda platform identity, not that admin account's name. False for
     *  every ordinary member post and for an admin-published post attributed to a real member. */
    @Column(name = "posted_as_platform", nullable = false)
    @Builder.Default
    private boolean postedAsPlatform = false;

    /** Which publisher identity to display for a platform post; ignored (left at its default) for
     *  every ordinary member post since display logic always gates on {@code postedAsPlatform} first. */
    @Enumerated(EnumType.STRING)
    @Column(name = "publisher_identity", nullable = false, length = 30)
    @Builder.Default
    private PublisherIdentity publisherIdentity = PublisherIdentity.ARJUN_MEHTA;

    /** Seeded/platform-level engagement ADDED to the real likesCount internally so a freshly-
     *  published platform post doesn't start from zero — never rendered as a number anywhere in
     *  member-facing UI (see PostCard.tsx), never called "likes" in any label or comment, since it
     *  isn't real users liking anything. Never backed by a PostLike row: never returned by the liker
     *  list ({@link com.nukkad.feed.repository.PostLikeRepository} is the only source for that), never
     *  touched by {@code toggleLike}, and deliberately excluded from feed-ranking's engagement-velocity
     *  signal (see PersonalizedFeedService, which reads only the real likesCount) so seeded engagement
     *  can never inflate what a real user's like earns a post in ranking. Meaningful only for a
     *  platform post; stays 0 for every member post. Every eligible platform post is floored at 15
     *  (see V111). */
    @Column(name = "platform_engagement_count", nullable = false)
    @Builder.Default
    private int platformEngagementCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Type type = Type.text;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "related_id", columnDefinition = "CHAR(36)")
    private String relatedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    /** An optional http(s) link the author attached (shown as a link card). */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** Null for every post that isn't a discussion (and for a discussion created through the plain
     *  generic Feed composer, which has no topic picker) — the Discussions UI treats a null topic
     *  as "General" rather than requiring a value. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Topic topic;

    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private int likesCount = 0;

    @Column(name = "comments_count", nullable = false)
    @Builder.Default
    private int commentsCount = 0;

    @Column(name = "hide_like_count", nullable = false)
    @Builder.Default
    private boolean hideLikeCount = false;

    @Column(name = "comments_disabled", nullable = false)
    @Builder.Default
    private boolean commentsDisabled = false;

    @Column(name = "removed_by_admin", nullable = false)
    @Builder.Default
    private boolean removedByAdmin = false;

    @Column(name = "removal_reason", length = 500)
    private String removalReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder asc")
    @Builder.Default
    private List<PostAttachment> attachments = new ArrayList<>();
}
