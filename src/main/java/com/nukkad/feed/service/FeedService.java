package com.nukkad.feed.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.paging.PageRequests;
import com.nukkad.common.publishing.PlatformAuthorResolver;
import com.nukkad.common.publishing.PublisherIdentity;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.feed.dto.AttachmentDto;
import com.nukkad.feed.dto.AttachmentRef;
import com.nukkad.feed.dto.CommentDto;
import com.nukkad.feed.dto.CreateCommentRequest;
import com.nukkad.feed.dto.CreatePostRequest;
import com.nukkad.feed.dto.PostDto;
import com.nukkad.feed.dto.PostLikeDto;
import com.nukkad.feed.dto.UpdatePostRequest;
import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.PostAttachment;
import com.nukkad.feed.entity.PostComment;
import com.nukkad.feed.entity.PostHide;
import com.nukkad.feed.entity.PostInteraction;
import com.nukkad.feed.entity.PostLike;
import com.nukkad.feed.entity.PostSave;
import com.nukkad.feed.dto.TrendingTopicDto;
import com.nukkad.feed.entity.PostHashtag;
import com.nukkad.feed.recommendation.UserTopicAffinityService;
import com.nukkad.feed.repository.PostCommentRepository;
import com.nukkad.feed.repository.PostHashtagRepository;
import com.nukkad.feed.repository.PostHideRepository;
import com.nukkad.feed.repository.PostInteractionRepository;
import com.nukkad.feed.repository.PostLikeRepository;
import com.nukkad.feed.repository.PostRepository;
import com.nukkad.feed.repository.PostSaveRepository;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FeedService {

    /** Every attachment key this service ever stores lives under this prefix — used both to store new
     *  ones and, in {@link #create}, to reject a client-supplied key that doesn't (see that method). */
    private static final String FEED_ATTACHMENT_SUBDIR = "feed";

    /** How long a minted attachment URL stays valid — mirrors ConversationService's own
     *  ATTACHMENT_URL_TTL (same reasoning: long enough that a normal open-tab session never sees a
     *  broken image, short enough that a leaked URL isn't a standing door; a refetch mints a fresh one). */
    private static final Duration ATTACHMENT_URL_TTL = Duration.ofHours(6);

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostSaveRepository postSaveRepository;
    private final PostHideRepository postHideRepository;
    private final PostInteractionRepository postInteractionRepository;
    private final UserTopicAffinityService userTopicAffinityService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final ConnectionRepository connectionRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final PlatformAuthorResolver platformAuthorResolver;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public FeedService(PostRepository postRepository, PostLikeRepository postLikeRepository,
                        PostCommentRepository postCommentRepository, PostSaveRepository postSaveRepository,
                        PostHideRepository postHideRepository, PostInteractionRepository postInteractionRepository,
                        UserTopicAffinityService userTopicAffinityService,
                        FileStorageService fileStorageService, AuditService auditService,
                        ConnectionRepository connectionRepository, PostHashtagRepository postHashtagRepository,
                        PlatformAuthorResolver platformAuthorResolver, NotificationService notificationService,
                        UserRepository userRepository) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.postCommentRepository = postCommentRepository;
        this.postSaveRepository = postSaveRepository;
        this.postHideRepository = postHideRepository;
        this.postInteractionRepository = postInteractionRepository;
        this.userTopicAffinityService = userTopicAffinityService;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
        this.connectionRepository = connectionRepository;
        this.postHashtagRepository = postHashtagRepository;
        this.platformAuthorResolver = platformAuthorResolver;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    /** Records a real behavioral-signal row and bumps the viewer's topic affinity — called after
     *  the triggering action (like/save/comment/hide/open) has already succeeded, never instead
     *  of it, so a signal-recording failure can't be confused with the action itself failing. */
    private void recordInteraction(String viewerId, Post post, PostInteraction.Type type) {
        postInteractionRepository.save(PostInteraction.builder().userId(viewerId).postId(post.getId()).interactionType(type).build());
        userTopicAffinityService.recordSignal(viewerId, post, type);
    }

    @Transactional(readOnly = true)
    public Page<PostDto> list(String viewerId, String authorId, String type, String tag, String chapterId, int page, int size) {
        Pageable pageable = PageRequests.of(page, size);
        String author = (authorId == null || authorId.isBlank()) ? null : authorId;
        Post.Type typeFilter = (type == null || type.isBlank()) ? null : parseType(type);
        // Only posts this viewer may read (public, their own, or connections-only from a connection).
        String tagFilter = (tag == null || tag.isBlank()) ? null : normalizedTagOrNoMatch(tag);
        String chapter = (chapterId == null || chapterId.isBlank()) ? null : chapterId;
        Page<Post> posts = postRepository.findVisibleTo(viewerId, author, typeFilter, tagFilter, chapter, pageable);
        List<PostDto> dtos = toDtoList(posts.getContent(), viewerId);
        return new PageImpl<>(dtos, pageable, posts.getTotalElements());
    }

    /** The like/save batching every list-of-posts endpoint needs — exactly 2 extra queries
     *  regardless of how many posts are in {@code posts}, never N+1. Used by {@link #list} and by
     *  {@code PersonalizedFeedService} so the personalized feed doesn't re-implement this. */
    @Transactional(readOnly = true)
    public List<PostDto> toDtoList(List<Post> posts, String viewerId) {
        List<String> postIds = posts.stream().map(Post::getId).toList();
        Set<String> likedIds = postIds.isEmpty() ? Set.of() : postLikeRepository.findLikedPostIds(viewerId, postIds);
        Set<String> savedIds = postIds.isEmpty() ? Set.of() : postSaveRepository.findSavedPostIds(viewerId, postIds);
        return posts.stream().map(p -> toDto(p, likedIds.contains(p.getId()), savedIds.contains(p.getId()))).toList();
    }

    public enum SavedPostsSort { NEWEST_SAVED, OLDEST_SAVED, NEWEST_POST, OLDEST_POST }

    /**
     * Dedicated saved-posts query — unlike {@link #list}, this never depends on where a post falls
     * in the main feed's own ordering, so a post saved long ago (and long since scrolled past in the
     * feed) is always reachable here. Exactly 3 queries regardless of page size: the paginated
     * PostSave page, a batch fetch of the matching Post rows, and a batch fetch of like status —
     * no N+1. isSaved is always true by construction (every row here is one of the viewer's own
     * saves), so unlike {@link #list} there's no need to re-check it against a fetched id set.
     */
    @Transactional(readOnly = true)
    public Page<PostDto> listSaved(String viewerId, String type, String sort, int page, int size) {
        Post.Type typeFilter = (type == null || type.isBlank()) ? null : parseType(type);
        Pageable pageable = PageRequests.of(page, size);

        Page<PostSave> saves = switch (parseSavedSort(sort)) {
            case OLDEST_SAVED -> postSaveRepository.findByUserOrderBySavedAtAsc(viewerId, typeFilter, pageable);
            case NEWEST_POST -> postSaveRepository.findByUserOrderByPostCreatedAtDesc(viewerId, typeFilter, pageable);
            case OLDEST_POST -> postSaveRepository.findByUserOrderByPostCreatedAtAsc(viewerId, typeFilter, pageable);
            case NEWEST_SAVED -> postSaveRepository.findByUserOrderBySavedAtDesc(viewerId, typeFilter, pageable);
        };

        List<String> postIds = saves.getContent().stream().map(PostSave::getPostId).toList();
        Map<String, Instant> savedAtByPostId = saves.getContent().stream()
                .collect(Collectors.toMap(PostSave::getPostId, PostSave::getCreatedAt, (a, b) -> a));
        Map<String, Post> postsById = postIds.isEmpty() ? Map.of()
                : postRepository.findAllById(postIds).stream().collect(Collectors.toMap(Post::getId, p -> p));
        Set<String> likedIds = postIds.isEmpty() ? Set.of() : postLikeRepository.findLikedPostIds(viewerId, postIds);

        // The FK cascade on post_saves guarantees a save row can't outlive its post, but this is
        // still a second, separate query a moment later — never trust that gap blindly.
        List<PostDto> content = postIds.stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .filter(p -> !p.isRemovedByAdmin())
                .map(p -> toDto(p, likedIds.contains(p.getId()), true, savedAtByPostId.get(p.getId())))
                .toList();

        return new PageImpl<>(content, pageable, saves.getTotalElements());
    }

    private SavedPostsSort parseSavedSort(String sort) {
        if (sort == null || sort.isBlank()) return SavedPostsSort.NEWEST_SAVED;
        return switch (sort) {
            case "newestSaved" -> SavedPostsSort.NEWEST_SAVED;
            case "oldestSaved" -> SavedPostsSort.OLDEST_SAVED;
            case "newestPost" -> SavedPostsSort.NEWEST_POST;
            case "oldestPost" -> SavedPostsSort.OLDEST_POST;
            default -> throw new BadRequestException("Invalid sort: " + sort);
        };
    }

    @Transactional
    public PostDto create(String authorId, CreatePostRequest request) {
        return create(authorId, request, false, PublisherIdentity.ARJUN_MEHTA, 0);
    }

    @Transactional
    public PostDto create(String authorId, CreatePostRequest request, boolean postedAsPlatform,
                           PublisherIdentity publisherIdentity, int platformEngagementCount) {
        String content = request.content() == null ? "" : request.content().trim();
        List<AttachmentRef> attachmentRefs = request.attachments() == null ? List.of() : request.attachments();
        String linkUrl = cleanLink(request.linkUrl());
        if (content.isEmpty() && attachmentRefs.isEmpty() && linkUrl == null) {
            throw new BadRequestException("A post needs text, a link or at least one attachment");
        }

        Post.Type type = parseType(request.type());
        Post.Visibility visibility = parseVisibility(request.visibility());
        // Best-effort: authorId is the authenticated principal, so this is never actually absent in
        // practice — but a post's chapter tagging isn't worth hard-failing creation over, so a
        // missing lookup just means "no chapter" rather than a thrown error.
        String chapterId = userRepository.findById(authorId).map(User::getChapterId).orElse(null);

        Post post = Post.builder()
                .authorId(authorId)
                .chapterId(chapterId)
                .postedAsPlatform(postedAsPlatform)
                .publisherIdentity(publisherIdentity)
                .platformEngagementCount(platformEngagementCount)
                .type(type)
                .content(content)
                .relatedId(request.relatedId())
                .visibility(visibility)
                .linkUrl(linkUrl)
                .build();

        for (int i = 0; i < attachmentRefs.size(); i++) {
            AttachmentRef ref = attachmentRefs.get(i);
            // An attachment must be a key this server itself minted via /feed/attachments (always under
            // FEED_ATTACHMENT_SUBDIR/) — not an arbitrary client-supplied string. Without this, a caller
            // could hand back e.g. a javascript: URI (served to every public viewer as attachment
            // content), or — now that attachments are private, stored+presigned by key rather than by a
            // world-readable URL — the key of a file from an unrelated, more sensitive private prefix
            // (like a chat attachment under messages/), which this code would otherwise happily presign
            // and embed into a public post, leaking a file its real owner never made public.
            if (!ref.url().startsWith(FEED_ATTACHMENT_SUBDIR + "/") || ref.url().contains("..")) {
                throw new BadRequestException("Invalid attachment");
            }
            post.getAttachments().add(PostAttachment.builder()
                    .post(post)
                    .url(ref.url())
                    .kind(parseKind(ref.kind()))
                    .fileName(ref.fileName())
                    .sortOrder(i)
                    .build());
        }

        Post saved = postRepository.save(post);
        saveHashtags(saved);
        return toDto(saved, false, false);
    }

    /**
     * An admin publishing a post from the admin panel. With {@code authorEmail}, that member becomes the
     * author (and is told, since it now appears as theirs); without it the admin's own account is the author
     * and the post is a platform post, displayed under {@code publisherIdentityRaw} (or the default identity
     * if blank/omitted) instead of that admin's real name. {@code publisherIdentityRaw}/{@code platformEngagementCount}
     * are only ever applied for a platform post — attributing to a real member ignores both, since they
     * describe how the platform-as-publisher should look, not that member's own post. Reuses {@link #create}
     * exactly as a member's own post would — no separate moderation gate exists for Feed posts to bypass.
     */
    @Transactional
    public PostDto createAsAdmin(String adminId, CreatePostRequest request, String authorEmail,
                                  String publisherIdentityRaw, Integer platformEngagementCount, String ip) {
        if (platformEngagementCount != null && platformEngagementCount < 0) {
            throw new BadRequestException("Platform engagement can't be negative");
        }
        String authorId = platformAuthorResolver.resolve(adminId, authorEmail);
        boolean postedAsPlatform = authorId.equals(adminId);
        PublisherIdentity publisherIdentity = postedAsPlatform
                ? PublisherIdentity.parse(publisherIdentityRaw, PublisherIdentity.ARJUN_MEHTA)
                : PublisherIdentity.ARJUN_MEHTA;
        int engagement = postedAsPlatform && platformEngagementCount != null ? platformEngagementCount : 0;
        PostDto created = create(authorId, request, postedAsPlatform, publisherIdentity, engagement);

        auditService.log(adminId, AuditAction.ADMIN_POST_CREATED, "Post", created.id(), ip, Map.of());

        if (!authorId.equals(adminId)) {
            notificationService.notify(authorId, NotificationType.post, "A post was added for you",
                    "A post was added to BuildAdda for you.", created.id(), adminId);
        }
        return created;
    }

    @Transactional
    public PostDto toggleLike(String viewerId, String postId) {
        requireVisiblePost(viewerId, postId);

        var existing = postLikeRepository.findByPostIdAndUserId(postId, viewerId);
        boolean liked;
        if (existing.isPresent()) {
            postLikeRepository.deleteByPostIdAndUserId(postId, viewerId);
            postRepository.decrementLikesCount(postId);
            liked = false;
        } else {
            // saveAndFlush (not save()) so the insert hits the database immediately: a plain save()
            // only queues it on the persistence context, so the very next toggle would never find it
            // and would insert a duplicate "like" instead of unliking. Going through the repository
            // method (rather than an injected EntityManager.flush()) also means a concurrent duplicate
            // insert's constraint/lock failure is properly translated into a Spring DataAccessException
            // for the controller to catch, instead of leaking a raw Hibernate/JPA exception type.
            postLikeRepository.saveAndFlush(PostLike.builder().postId(postId).userId(viewerId).build());
            postRepository.incrementLikesCount(postId);
            liked = true;
        }
        // The modifying query above clears the persistence context, so this is a fresh read.
        Post refreshed = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        recordInteraction(viewerId, refreshed, liked ? PostInteraction.Type.LIKE : PostInteraction.Type.UNLIKE);
        boolean saved = postSaveRepository.findByPostIdAndUserId(postId, viewerId).isPresent();
        return toDto(refreshed, liked, saved);
    }

    @Transactional
    public PostDto toggleSave(String viewerId, String postId) {
        Post post = requireVisiblePost(viewerId, postId);

        var existing = postSaveRepository.findByPostIdAndUserId(postId, viewerId);
        boolean saved;
        if (existing.isPresent()) {
            postSaveRepository.deleteByPostIdAndUserId(postId, viewerId);
            saved = false;
        } else {
            postSaveRepository.save(PostSave.builder().postId(postId).userId(viewerId).build());
            saved = true;
        }
        recordInteraction(viewerId, post, saved ? PostInteraction.Type.SAVE : PostInteraction.Type.UNSAVE);
        boolean liked = postLikeRepository.findByPostIdAndUserId(postId, viewerId).isPresent();
        return toDto(post, liked, saved);
    }

    /** The only "share" this app has is forwarding a post into a DM (see ConversationService) —
     *  called right after that succeeds, so a share still counts as a real signal even though
     *  there's no public repost/share count to back it with. */
    @Transactional
    public void recordShare(String viewerId, String postId) {
        Post post = requireVisiblePost(viewerId, postId);
        recordInteraction(viewerId, post, PostInteraction.Type.SHARE);
    }

    /** Mirrors {@link #toggleSave} exactly — the personalized feed's strong-negative signal and
     *  its own candidate-pool exclusion (see PostHideRepository), never a topic-level exclusion:
     *  liking other posts in the same topic still raises it, this specific post just won't
     *  reappear in the viewer's personalized feed. */
    @Transactional
    public PostDto toggleHide(String viewerId, String postId) {
        Post post = requireVisiblePost(viewerId, postId);

        var existing = postHideRepository.findByPostIdAndUserId(postId, viewerId);
        boolean hidden;
        if (existing.isPresent()) {
            postHideRepository.deleteByPostIdAndUserId(postId, viewerId);
            hidden = false;
        } else {
            postHideRepository.save(PostHide.builder().postId(postId).userId(viewerId).build());
            hidden = true;
        }
        recordInteraction(viewerId, post, hidden ? PostInteraction.Type.HIDE : PostInteraction.Type.UNHIDE);
        boolean liked = postLikeRepository.findByPostIdAndUserId(postId, viewerId).isPresent();
        boolean saved = postSaveRepository.findByPostIdAndUserId(postId, viewerId).isPresent();
        return toDto(post, liked, saved);
    }

    @Transactional
    public PostDto get(String viewerId, String postId) {
        Post post = requireVisiblePost(viewerId, postId);
        if (post.isRemovedByAdmin()) {
            throw new ResourceNotFoundException("Post not found: " + postId);
        }
        // Never record a viewer opening their own post — that's not a discovery signal, and would
        // otherwise fire on every single glance at something the author already wrote.
        if (!post.getAuthorId().equals(viewerId)) {
            recordInteraction(viewerId, post, PostInteraction.Type.OPEN);
        }
        return toDto(post, viewerId);
    }

    /** The post's author, but only once visibility is confirmed the same way {@link #get} does —
     *  used by ReportService.submit so reporting a postId can't be used to probe whether a post
     *  outside the reporter's own visibility (e.g. a connections-only post from a stranger) exists:
     *  "not visible" and "doesn't exist" both surface as the identical 404 here, exactly as they do
     *  for a direct {@code GET} on the post. */
    @Transactional(readOnly = true)
    public String requireVisibleAuthorId(String viewerId, String postId) {
        return requireVisiblePost(viewerId, postId).getAuthorId();
    }

    // ADMIN-ONLY — bypasses the removed check above.
    @Transactional(readOnly = true)
    public PostDto getForAdmin(String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return toDto(post, false, false);
    }

    // ADMIN-ONLY listing — never excludes removed posts; includeRemoved just narrows the choice
    // between "everything" and "only what's currently live", mirroring the other Admin*Controllers.
    @Transactional(readOnly = true)
    public Page<PostDto> listForAdmin(boolean includeRemoved, int page, int size) {
        Pageable pageable = PageRequests.of(page, size);
        Page<Post> posts = includeRemoved
                ? postRepository.findAllByOrderByCreatedAtDesc(pageable)
                : postRepository.findByRemovedByAdminFalseOrderByCreatedAtDesc(pageable);
        return posts.map(p -> toDto(p, false, false));
    }

    // Admin-only moderation toggle — mirrors IdeaService.setRemovedByAdmin exactly. There's no
    // pre-publish queue for posts (that would gut the feed's real-time nature); this reactive
    // takedown, plus the report path in ReportService, is the moderation lever for Feed.
    @Transactional
    public PostDto setRemovedByAdmin(String adminId, String postId, boolean removed, String reason, String ip) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        post.setRemovedByAdmin(removed);
        post.setRemovalReason(removed ? reason : null);
        post = postRepository.save(post);

        auditService.log(adminId, removed ? AuditAction.ADMIN_CONTENT_REMOVED : AuditAction.ADMIN_CONTENT_RESTORED,
                "Post", postId, ip, removed && reason != null && !reason.isBlank()
                        ? Map.of("entityType", "Post", "reason", reason) : Map.of("entityType", "Post"));

        return toDto(post, false, false);
    }

    @Transactional
    public void delete(String viewerId, String postId) {
        Post post = requireOwnedPost(viewerId, postId);
        // A legacy attachment is still a full public URL (deleteIfHosted); every attachment stored
        // since attachments became private is a bare key (deleteByKey) — see resolveAttachmentUrl.
        post.getAttachments().forEach(a -> {
            if (fileStorageService.isHostedUrl(a.getUrl())) {
                fileStorageService.deleteIfHosted(a.getUrl());
            } else {
                fileStorageService.deleteByKey(a.getUrl());
            }
        });
        postRepository.delete(post);
    }

    @Transactional
    public PostDto update(String viewerId, String postId, UpdatePostRequest request) {
        Post post = requireOwnedPost(viewerId, postId);
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty() && post.getAttachments().isEmpty() && post.getLinkUrl() == null) {
            throw new BadRequestException("A post needs text, a link or at least one attachment");
        }
        post.setContent(content);
        postRepository.save(post);
        postHashtagRepository.deleteByPostId(post.getId());
        saveHashtags(post);
        return toDto(post, viewerId);
    }

    @Transactional
    public PostDto toggleHideLikeCount(String viewerId, String postId) {
        Post post = requireOwnedPost(viewerId, postId);
        post.setHideLikeCount(!post.isHideLikeCount());
        postRepository.save(post);
        return toDto(post, viewerId);
    }

    @Transactional
    public PostDto toggleCommentsDisabled(String viewerId, String postId) {
        Post post = requireOwnedPost(viewerId, postId);
        post.setCommentsDisabled(!post.isCommentsDisabled());
        postRepository.save(post);
        return toDto(post, viewerId);
    }

    /** Records the #hashtags in the post's current text (call after clearing the old ones when editing). */
    private void saveHashtags(Post post) {
        Set<String> tags = Hashtags.extract(post.getContent());
        if (tags.isEmpty()) return;
        postHashtagRepository.saveAll(tags.stream()
                .map(tag -> PostHashtag.builder().postId(post.getId()).tag(tag).build())
                .toList());
    }

    /** The tag as stored; a value that can never be a tag matches nothing rather than turning into "no filter". */
    private String normalizedTagOrNoMatch(String tag) {
        String normalized = Hashtags.normalize(tag);
        return normalized != null ? normalized : "no such tag";
    }

    /** The most-used hashtags among posts from the last {@code days} days that {@code viewerId} may read. */
    @Transactional(readOnly = true)
    public List<TrendingTopicDto> trendingTopics(String viewerId, int days, int limit) {
        int windowDays = Math.max(1, Math.min(days, 90));
        int topics = Math.max(1, Math.min(limit, 20));
        Instant since = Instant.now().minus(Duration.ofDays(windowDays));
        return postHashtagRepository.findTrending(viewerId, since, PageRequest.of(0, topics)).stream()
                .map(row -> new TrendingTopicDto(row.getTag(), row.getPostCount()))
                .toList();
    }

    private Post requireOwnedPost(String viewerId, String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        if (!post.getAuthorId().equals(viewerId)) {
            throw new ForbiddenException("You can only manage your own posts");
        }
        return post;
    }

    /**
     * The post, or "not found" when it doesn't exist OR the viewer isn't allowed to read it: a connections-only post
     * answers exactly like a missing one, so its existence isn't revealed to people it was hidden from.
     */
    private Post requireVisiblePost(String viewerId, String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        if (!canView(post, viewerId)) {
            throw new ResourceNotFoundException("Post not found: " + postId);
        }
        return post;
    }

    /** Same rule as {@code PostVisibilityQuery}: public, the viewer's own, or a connection's when connections-only. */
    private boolean canView(Post post, String viewerId) {
        return post.getVisibility() == Post.Visibility.PUBLIC
                || post.getAuthorId().equals(viewerId)
                || connectionRepository.existsAcceptedBetween(post.getAuthorId(), viewerId);
    }

    @Transactional(readOnly = true)
    public Page<CommentDto> listComments(String viewerId, String postId, int page, int size) {
        requireVisiblePost(viewerId, postId);
        Page<PostComment> comments = postCommentRepository
                .findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(postId, PageRequests.of(page, size));

        List<String> ids = comments.getContent().stream().map(PostComment::getId).toList();
        Map<String, Integer> replyCounts = ids.isEmpty() ? Map.of() : postCommentRepository
                .countRepliesGroupedByParent(ids).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Long) row[1]).intValue()));

        return comments.map(c -> toCommentDto(c, replyCounts.getOrDefault(c.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public Page<CommentDto> listReplies(String viewerId, String postId, String commentId, int page, int size) {
        requireVisiblePost(viewerId, postId);
        PostComment parent = postCommentRepository.findById(commentId)
                .filter(c -> c.getPostId().equals(postId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        if (parent.getParentCommentId() != null) {
            throw new BadRequestException("Cannot list replies of a reply");
        }
        return postCommentRepository.findByParentCommentIdOrderByCreatedAtAsc(commentId, PageRequests.of(page, size))
                .map(c -> toCommentDto(c, 0));
    }

    @Transactional
    public CommentDto addComment(String authorId, String postId, CreateCommentRequest request) {
        Post post = requireVisiblePost(authorId, postId);
        if (post.isCommentsDisabled()) {
            throw new BadRequestException("Comments are turned off for this post");
        }

        // Flattening rule: replies never nest past one level. Replying to a reply re-parents onto
        // that reply's own top-level ancestor instead of rejecting it, so "reply to a reply" still
        // works from the user's perspective — it just lands in the same flat list as every other
        // reply under that original comment.
        String parentCommentId = null;
        if (request.parentCommentId() != null && !request.parentCommentId().isBlank()) {
            PostComment target = postCommentRepository.findById(request.parentCommentId())
                    .filter(c -> c.getPostId().equals(postId))
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + request.parentCommentId()));
            parentCommentId = target.getParentCommentId() != null ? target.getParentCommentId() : target.getId();
        }

        // saveAndFlush (not save()) so the insert actually reaches the database before the next
        // line runs: incrementCommentsCount is a bulk @Modifying update with clearAutomatically =
        // true, which clears the persistence context immediately after executing. A plain save()
        // only queues the insert — it's still sitting unflushed when clear() runs, so it gets
        // silently discarded and the comment never reaches the database (commentsCount still
        // increments correctly since that's a separate direct UPDATE, which is why the count went
        // up while the comment itself never appeared). Same class of bug already fixed this way in
        // toggleLike above.
        PostComment comment = postCommentRepository.saveAndFlush(PostComment.builder()
                .postId(postId)
                .parentCommentId(parentCommentId)
                .authorId(authorId)
                .content(request.content().trim())
                .build());

        postRepository.incrementCommentsCount(postId);
        recordInteraction(authorId, post, PostInteraction.Type.COMMENT);

        return toCommentDto(comment, 0);
    }

    @Transactional
    public void deleteComment(String viewerId, String postId, String commentId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        PostComment comment = postCommentRepository.findById(commentId)
                .filter(c -> c.getPostId().equals(postId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        if (!comment.getAuthorId().equals(viewerId) && !post.getAuthorId().equals(viewerId)) {
            throw new ForbiddenException("You can only delete your own comments");
        }

        int removed = 1 + (comment.getParentCommentId() == null
                ? (int) postCommentRepository.countByParentCommentId(commentId)
                : 0);
        postCommentRepository.delete(comment); // DB cascade (ON DELETE CASCADE) removes any replies
        postRepository.decrementCommentsCount(postId, removed);
    }

    @Transactional(readOnly = true)
    public Page<PostLikeDto> listLikers(String viewerId, String postId, int page, int size) {
        requireVisiblePost(viewerId, postId);
        return postLikeRepository.findByPostIdOrderByCreatedAtDesc(postId, PageRequests.of(page, size))
                .map(l -> new PostLikeDto(l.getUserId(), l.getCreatedAt()));
    }

    public AttachmentRef uploadAttachment(MultipartFile file) {
        var stored = fileStorageService.storePrivateFeedAttachment(file, FEED_ATTACHMENT_SUBDIR);
        String originalName = file.getOriginalFilename();
        return new AttachmentRef(stored.key(), stored.kind().name(), originalName);
    }

    private Post.Type parseType(String type) {
        if (type == null || type.isBlank()) return Post.Type.text;
        try {
            return Post.Type.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid post type: " + type);
        }
    }

    private Post.Visibility parseVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) return Post.Visibility.PUBLIC;
        try {
            return Post.Visibility.valueOf(visibility.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid visibility: " + visibility);
        }
    }

    /** Null for "no link"; otherwise the trimmed link, which must be an absolute http(s) URL with a host. */
    private String cleanLink(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String link = raw.trim();
        if (link.length() > 500) {
            throw new BadRequestException("That link is too long. Links can be up to 500 characters.");
        }
        try {
            URI uri = new URI(link);
            String scheme = uri.getScheme();
            boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!web || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BadRequestException("Enter a link that starts with http:// or https://");
            }
        } catch (URISyntaxException e) {
            throw new BadRequestException("Enter a link that starts with http:// or https://");
        }
        return link;
    }

    private PostAttachment.Kind parseKind(String kind) {
        try {
            return PostAttachment.Kind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid attachment kind: " + kind);
        }
    }

    private PostDto toDto(Post post, String viewerId) {
        boolean isLiked = postLikeRepository.findByPostIdAndUserId(post.getId(), viewerId).isPresent();
        boolean isSaved = postSaveRepository.findByPostIdAndUserId(post.getId(), viewerId).isPresent();
        return toDto(post, isLiked, isSaved);
    }

    private PostDto toDto(Post post, boolean isLiked, boolean isSaved) {
        return toDto(post, isLiked, isSaved, null);
    }

    private PostDto toDto(Post post, boolean isLiked, boolean isSaved, Instant savedAt) {
        List<AttachmentDto> attachments = post.getAttachments().stream()
                .map(a -> new AttachmentDto(a.getId(), resolveAttachmentUrl(a.getUrl()), a.getKind().name(), a.getFileName()))
                .toList();
        return new PostDto(post.getId(), post.getAuthorId(), post.getChapterId(), post.getType().name(), post.getContent(), post.getRelatedId(),
                post.getLikesCount(), post.getCommentsCount(), isLiked, isSaved, post.isHideLikeCount(), post.isCommentsDisabled(),
                post.getCreatedAt(), attachments, savedAt, post.isRemovedByAdmin(), post.getRemovalReason(),
                post.getVisibility().name(), post.getLinkUrl(), post.isPostedAsPlatform(),
                post.getPublisherIdentity().name(), post.getPlatformEngagementCount());
    }

    /** {@code stored} is either a legacy full public URL (from before attachments were made private —
     *  the live bucket policy since blocked anonymous reads of {@code feed/*}, so that URL now 403s
     *  unless re-presigned the same as a private key below) or, for every attachment stored since,
     *  the private key {@link #uploadAttachment} minted — which needs a fresh presigned URL on every
     *  read, the same way ConversationService already does for chat attachments. */
    private String resolveAttachmentUrl(String stored) {
        String key = fileStorageService.isHostedUrl(stored) ? fileStorageService.keyFromHostedUrl(stored) : stored;
        return fileStorageService.presignGet(key, ATTACHMENT_URL_TTL);
    }

    private CommentDto toCommentDto(PostComment comment, int replyCount) {
        return new CommentDto(comment.getId(), comment.getPostId(), comment.getParentCommentId(), comment.getAuthorId(),
                comment.getContent(), replyCount, comment.getCreatedAt());
    }
}
