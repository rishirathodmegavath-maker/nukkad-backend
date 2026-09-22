package com.nukkad.discussion.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.discussion.dto.CreateDiscussionRequest;
import com.nukkad.discussion.dto.DiscussionCommentDto;
import com.nukkad.discussion.dto.DiscussionDto;
import com.nukkad.discussion.dto.DiscussionStatsDto;
import com.nukkad.discussion.dto.TopicCountDto;
import com.nukkad.discussion.entity.PostCommentLike;
import com.nukkad.discussion.entity.PostFollow;
import com.nukkad.discussion.entity.PostVote;
import com.nukkad.discussion.entity.PostView;
import com.nukkad.discussion.repository.PostCommentLikeRepository;
import com.nukkad.discussion.repository.PostFollowRepository;
import com.nukkad.discussion.repository.PostVoteRepository;
import com.nukkad.discussion.repository.PostViewRepository;
import com.nukkad.feed.dto.AttachmentDto;
import com.nukkad.feed.dto.AttachmentRef;
import com.nukkad.feed.dto.CommentDto;
import com.nukkad.feed.dto.CreateCommentRequest;
import com.nukkad.feed.dto.PostDto;
import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.PostAttachment;
import com.nukkad.feed.entity.PostComment;
import com.nukkad.feed.entity.PostHashtag;
import com.nukkad.feed.repository.PostCommentRepository;
import com.nukkad.feed.repository.PostHashtagRepository;
import com.nukkad.feed.repository.PostLikeRepository;
import com.nukkad.feed.repository.PostRepository;
import com.nukkad.feed.repository.PostSaveRepository;
import com.nukkad.feed.service.FeedService;
import com.nukkad.feed.service.Hashtags;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything Discussions needs that plain Feed posts never did: a curated topic, view/vote/follow
 * tracking and per-reply likes. A discussion is still just a {@code Post} with {@code type=discussion}
 * — creation, reading, liking, saving and commenting all reuse {@link FeedService}'s existing,
 * already-tested methods (see {@link #create}, {@link #toggleCommentLike}'s sibling comment methods
 * below); this service only adds the genuinely new numbers on top and builds the richer
 * {@link DiscussionDto} the Feed's own {@link PostDto} was never meant to carry.
 */
@Service
public class DiscussionService {

    private static final int TRENDING_POOL_SIZE = 200;
    private static final int TRENDING_WINDOW_DAYS = 14;
    private static final String NO_SUCH_TAG = "\u0000no such tag";
    private static final List<String> NO_SUCH_TAGS = List.of(NO_SUCH_TAG);

    public enum Sort { RECENT, TRENDING, UNANSWERED, FOLLOWING, MINE }

    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostSaveRepository postSaveRepository;
    private final PostViewRepository postViewRepository;
    private final PostVoteRepository postVoteRepository;
    private final PostFollowRepository postFollowRepository;
    private final PostCommentLikeRepository postCommentLikeRepository;
    private final FeedService feedService;

    public DiscussionService(PostRepository postRepository, PostCommentRepository postCommentRepository,
                              PostHashtagRepository postHashtagRepository, PostLikeRepository postLikeRepository,
                              PostSaveRepository postSaveRepository, PostViewRepository postViewRepository,
                              PostVoteRepository postVoteRepository, PostFollowRepository postFollowRepository,
                              PostCommentLikeRepository postCommentLikeRepository, FeedService feedService) {
        this.postRepository = postRepository;
        this.postCommentRepository = postCommentRepository;
        this.postHashtagRepository = postHashtagRepository;
        this.postLikeRepository = postLikeRepository;
        this.postSaveRepository = postSaveRepository;
        this.postViewRepository = postViewRepository;
        this.postVoteRepository = postVoteRepository;
        this.postFollowRepository = postFollowRepository;
        this.postCommentLikeRepository = postCommentLikeRepository;
        this.feedService = feedService;
    }

    // ---- Listing ----

    @Transactional(readOnly = true)
    public Page<DiscussionDto> list(String viewerId, String sort, String topicParam, String tagParam, int page, int size) {
        Post.Topic topic = parseTopicOrNull(topicParam);
        String tag = normalizedTagOrNoMatch(tagParam);
        Pageable pageable = PageRequest.of(page, size);
        Sort sortMode = parseSort(sort);

        Page<Post> posts = switch (sortMode) {
            case UNANSWERED -> postRepository.findDiscussions(viewerId, null, topic, tag, true, pageable);
            case MINE -> viewerId == null ? Page.empty(pageable)
                    : postRepository.findDiscussions(viewerId, viewerId, topic, tag, false, pageable);
            case FOLLOWING -> followingPage(viewerId, topic, tag, pageable);
            case TRENDING -> trendingPage(viewerId, topic, tag, pageable);
            case RECENT -> postRepository.findDiscussions(viewerId, null, topic, tag, false, pageable);
        };

        return toDtoPage(posts, viewerId);
    }

    private Page<Post> followingPage(String viewerId, Post.Topic topic, String tag, Pageable pageable) {
        if (viewerId == null) return Page.empty(pageable);
        List<String> ids = postFollowRepository.findPostIdsByUserId(viewerId);
        return ids.isEmpty() ? Page.empty(pageable) : postRepository.findDiscussionsByIds(viewerId, ids, topic, tag, pageable);
    }

    /**
     * Trending is the one sort that isn't a plain query: it scores a bounded, recent pool by real
     * votes+replies (never a fabricated "hot" number) and sorts/pages that in memory, since expressing
     * "order by net votes*2 + replies" as JPQL would need a correlated aggregate subquery per row.
     */
    private Page<Post> trendingPage(String viewerId, Post.Topic topic, String tag, Pageable pageable) {
        Instant since = Instant.now().minus(Duration.ofDays(TRENDING_WINDOW_DAYS));
        List<Post> pool = postRepository.findRecentDiscussionsForTrending(viewerId, since, topic, tag, PageRequest.of(0, TRENDING_POOL_SIZE));
        if (pool.isEmpty()) return Page.empty(pageable);

        List<String> ids = pool.stream().map(Post::getId).toList();
        Map<String, Integer> netScores = batchNetScores(ids);
        List<Post> sorted = pool.stream()
                .sorted(Comparator.<Post>comparingInt(p -> netScores.getOrDefault(p.getId(), 0) * 2 + p.getCommentsCount())
                        .reversed()
                        .thenComparing(Post::getCreatedAt, Comparator.reverseOrder()))
                .toList();

        int from = Math.min((int) pageable.getOffset(), sorted.size());
        int to = Math.min(from + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
    }

    // ---- Reading one discussion ----

    @Transactional(readOnly = true)
    public DiscussionDto get(String viewerId, String postId) {
        PostDto base = feedService.get(viewerId, postId); // visibility + removed checks already happen here
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return toDto(post, base, viewerId);
    }

    /** Fire-and-forget, same idea as {@code StartupService#recordProfileView}: never blocks or fails the
     *  read it's attached to, and excludes the discussion's own author from inflating their own count. */
    @Transactional
    public void recordView(String postId, String viewerId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null || (viewerId != null && viewerId.equals(post.getAuthorId()))) return;
        postViewRepository.save(PostView.builder().postId(postId).viewerId(viewerId).build());
    }

    // ---- Creating ----

    @Transactional
    public DiscussionDto create(String authorId, CreateDiscussionRequest request) {
        String content = request.content() == null ? "" : request.content().trim();
        List<AttachmentRef> attachmentRefs = request.attachments() == null ? List.of() : request.attachments();
        if (content.isEmpty() && attachmentRefs.isEmpty()) {
            throw new BadRequestException("A discussion needs some text or at least one attachment");
        }

        Post post = Post.builder()
                .authorId(authorId)
                .type(Post.Type.discussion)
                .content(content)
                .topic(parseTopicOrNull(request.topic()))
                .visibility(parseVisibility(request.visibility()))
                .linkUrl(request.linkUrl())
                .build();

        for (int i = 0; i < attachmentRefs.size(); i++) {
            AttachmentRef ref = attachmentRefs.get(i);
            post.getAttachments().add(PostAttachment.builder()
                    .post(post).url(ref.url()).kind(PostAttachment.Kind.valueOf(ref.kind()))
                    .fileName(ref.fileName()).sortOrder(i).build());
        }

        Post saved = postRepository.save(post);
        Set<String> tags = Hashtags.extract(saved.getContent());
        if (!tags.isEmpty()) {
            postHashtagRepository.saveAll(tags.stream().map(t -> PostHashtag.builder().postId(saved.getId()).tag(t).build()).toList());
        }

        return toDto(saved, false, false, false, 0, 0, 0L, 1, new ArrayList<>(tags), saved.getCreatedAt());
    }

    // ---- Voting ----

    /** Clicking the same direction again removes the vote; clicking the other direction switches it. */
    @Transactional
    public DiscussionDto castVote(String viewerId, String postId, String direction) {
        Post post = requireDiscussion(postId);
        int value = "up".equals(direction) ? 1 : -1;

        postVoteRepository.findByPostIdAndUserId(postId, viewerId).ifPresentOrElse(existing -> {
            if (existing.getValue() == value) {
                postVoteRepository.deleteByPostIdAndUserId(postId, viewerId);
            } else {
                existing.setValue(value);
                postVoteRepository.save(existing);
            }
        }, () -> postVoteRepository.save(PostVote.builder().postId(postId).userId(viewerId).value(value).build()));

        PostDto base = feedService.get(viewerId, postId);
        return toDto(post, base, viewerId);
    }

    // ---- Following ----

    public record FollowResult(boolean following) {}

    @Transactional
    public FollowResult toggleFollow(String viewerId, String postId) {
        requireDiscussion(postId);
        if (postFollowRepository.existsByUserIdAndPostId(viewerId, postId)) {
            postFollowRepository.deleteByUserIdAndPostId(viewerId, postId);
            return new FollowResult(false);
        }
        postFollowRepository.save(PostFollow.builder().userId(viewerId).postId(postId).build());
        return new FollowResult(true);
    }

    // ---- Comments (delegates to FeedService for everything but the like count) ----

    @Transactional(readOnly = true)
    public Page<DiscussionCommentDto> listComments(String viewerId, String postId, int page, int size) {
        Page<CommentDto> comments = feedService.listComments(viewerId, postId, page, size);
        List<String> ids = comments.getContent().stream().map(CommentDto::id).toList();
        Map<String, Integer> likeCounts = ids.isEmpty() ? Map.of() : postCommentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(PostComment::getId, PostComment::getLikesCount));
        Set<String> likedIds = ids.isEmpty() || viewerId == null ? Set.of() : postCommentLikeRepository.findLikedCommentIds(viewerId, ids);
        return comments.map(c -> toCommentDto(c, likeCounts.getOrDefault(c.id(), 0), likedIds.contains(c.id())));
    }

    @Transactional
    public DiscussionCommentDto addComment(String authorId, String postId, CreateCommentRequest request) {
        CommentDto comment = feedService.addComment(authorId, postId, request);
        return toCommentDto(comment, 0, false);
    }

    @Transactional
    public void deleteComment(String viewerId, String postId, String commentId) {
        feedService.deleteComment(viewerId, postId, commentId);
    }

    @Transactional
    public DiscussionCommentDto toggleCommentLike(String viewerId, String postId, String commentId) {
        feedService.get(viewerId, postId); // visibility + removed check, same as every other comment action
        PostComment comment = postCommentRepository.findById(commentId)
                .filter(c -> c.getPostId().equals(postId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        boolean liked;
        if (postCommentLikeRepository.findByCommentIdAndUserId(commentId, viewerId).isPresent()) {
            postCommentLikeRepository.deleteByCommentIdAndUserId(commentId, viewerId);
            postCommentRepository.decrementLikesCount(commentId);
            liked = false;
        } else {
            postCommentLikeRepository.save(PostCommentLike.builder().commentId(commentId).userId(viewerId).build());
            postCommentRepository.incrementLikesCount(commentId);
            liked = true;
        }
        PostComment refreshed = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        int replyCount = comment.getParentCommentId() == null ? (int) postCommentRepository.countByParentCommentId(commentId) : 0;
        return new DiscussionCommentDto(refreshed.getId(), refreshed.getPostId(), refreshed.getParentCommentId(),
                refreshed.getAuthorId(), refreshed.getContent(), replyCount, refreshed.getLikesCount(), liked, refreshed.getCreatedAt());
    }

    // ---- Topics, stats, related, participants ----

    @Transactional(readOnly = true)
    public List<TopicCountDto> listTopics() {
        Map<Post.Topic, Long> counts = new HashMap<>();
        for (Object[] row : postRepository.countDiscussionsByTopic()) {
            if (row[0] != null) counts.put((Post.Topic) row[0], (Long) row[1]);
        }
        return java.util.Arrays.stream(Post.Topic.values())
                .map(t -> new TopicCountDto(t.name(), t.getLabel(), counts.getOrDefault(t, 0L)))
                .sorted(Comparator.comparingLong(TopicCountDto::count).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public DiscussionStatsDto getStats() {
        long totalDiscussions = postRepository.countByTypeAndRemovedByAdminFalseAndVisibility(Post.Type.discussion, Post.Visibility.PUBLIC);
        long totalReplies = postCommentRepository.countCommentsOnDiscussions();
        Set<String> participants = new HashSet<>(postRepository.findDistinctDiscussionAuthorIds());
        participants.addAll(postCommentRepository.findDistinctCommenterIdsOnDiscussions());
        return new DiscussionStatsDto(totalDiscussions, totalReplies, participants.size());
    }

    @Transactional(readOnly = true)
    public List<DiscussionDto> listRelated(String viewerId, String postId, int limit) {
        Post post = requireDiscussion(postId);
        List<String> tags = postHashtagRepository.findTagsByPostId(postId);
        List<Post> related = postRepository.findRelatedDiscussions(viewerId, postId, post.getTopic(),
                tags.isEmpty() ? NO_SUCH_TAGS : tags, PageRequest.of(0, Math.max(1, limit)));
        return toDtoPage(new PageImpl<>(related), viewerId).getContent();
    }

    /** Author first, then everyone who replied, in the order they first did — a plain userId list; the
     *  frontend resolves each one the same way LikesModal already does (one useUser per id). */
    @Transactional(readOnly = true)
    public List<String> listParticipants(String postId) {
        Post post = requireDiscussion(postId);
        List<String> commenters = postCommentRepository.findDistinctAuthorIdsByPostId(postId);
        List<String> ordered = new ArrayList<>();
        ordered.add(post.getAuthorId());
        for (String id : commenters) {
            if (!ordered.contains(id)) ordered.add(id);
        }
        return ordered;
    }

    // ---- Shared mapping/batching ----

    private Post requireDiscussion(String postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        if (post.getType() != Post.Type.discussion) {
            throw new BadRequestException("This isn't a discussion");
        }
        return post;
    }

    private Page<DiscussionDto> toDtoPage(Page<Post> posts, String viewerId) {
        List<String> ids = posts.getContent().stream().map(Post::getId).toList();
        if (ids.isEmpty()) return posts.map(p -> null);

        Set<String> likedIds = viewerId == null ? Set.of() : postLikeRepository.findLikedPostIds(viewerId, ids);
        Set<String> savedIds = viewerId == null ? Set.of() : postSaveRepository.findSavedPostIds(viewerId, ids);
        Set<String> followedIds = viewerId == null ? Set.of() : postFollowRepository.findFollowedPostIds(viewerId, ids);
        Map<String, Integer> netScores = batchNetScores(ids);
        Map<String, Integer> myVotes = viewerId == null ? Map.of() : batchMyVotes(viewerId, ids);
        Map<String, Long> viewCounts = batchViewCounts(ids);
        Map<String, Integer> participantCounts = batchParticipantCounts(posts.getContent(), ids);
        Map<String, List<String>> tagsByPost = batchTags(ids);
        Map<String, Instant> lastActivity = batchLastActivity(ids);

        return posts.map(p -> toDto(p,
                likedIds.contains(p.getId()), savedIds.contains(p.getId()), followedIds.contains(p.getId()),
                netScores.getOrDefault(p.getId(), 0), myVotes.getOrDefault(p.getId(), 0),
                viewCounts.getOrDefault(p.getId(), 0L), participantCounts.getOrDefault(p.getId(), 1),
                tagsByPost.getOrDefault(p.getId(), List.of()), lastActivity.getOrDefault(p.getId(), p.getCreatedAt())));
    }

    private Map<String, Integer> batchNetScores(Collection<String> ids) {
        Map<String, Integer> scores = new HashMap<>();
        for (Object[] row : postVoteRepository.sumValueByPostIds(ids)) {
            scores.put((String) row[0], ((Number) row[1]).intValue());
        }
        return scores;
    }

    private Map<String, Integer> batchMyVotes(String viewerId, Collection<String> ids) {
        Map<String, Integer> votes = new HashMap<>();
        for (Object[] row : postVoteRepository.findValuesByUserIdAndPostIds(viewerId, ids)) {
            votes.put((String) row[0], (Integer) row[1]);
        }
        return votes;
    }

    private Map<String, Long> batchViewCounts(Collection<String> ids) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : postViewRepository.countByPostIds(ids)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** Participant = |{author} ∪ {distinct reply authors}| — real, derived, never stored. */
    private Map<String, Integer> batchParticipantCounts(Collection<Post> posts, Collection<String> ids) {
        Map<String, Set<String>> commentersByPost = new HashMap<>();
        for (Object[] row : postCommentRepository.findAuthorIdsByPostIds(ids)) {
            commentersByPost.computeIfAbsent((String) row[0], k -> new HashSet<>()).add((String) row[1]);
        }
        Map<String, Integer> counts = new HashMap<>();
        for (Post p : posts) {
            Set<String> all = new HashSet<>(commentersByPost.getOrDefault(p.getId(), Set.of()));
            all.add(p.getAuthorId());
            counts.put(p.getId(), all.size());
        }
        return counts;
    }

    private Map<String, List<String>> batchTags(Collection<String> ids) {
        Map<String, List<String>> tags = new HashMap<>();
        for (Object[] row : postHashtagRepository.findTagsByPostIds(ids)) {
            tags.computeIfAbsent((String) row[0], k -> new ArrayList<>()).add((String) row[1]);
        }
        return tags;
    }

    private Map<String, Instant> batchLastActivity(Collection<String> ids) {
        Map<String, Instant> latest = new HashMap<>();
        for (Object[] row : postCommentRepository.findLatestCommentTimeByPostIds(ids)) {
            latest.put((String) row[0], (Instant) row[1]);
        }
        return latest;
    }

    private DiscussionDto toDto(Post post, PostDto base, String viewerId) {
        List<String> tags = postHashtagRepository.findTagsByPostId(post.getId());
        long views = postViewRepository.countByPostId(post.getId());
        int netScore = postVoteRepository.sumValueByPostId(post.getId());
        int myVote = viewerId == null ? 0 : postVoteRepository.findByPostIdAndUserId(post.getId(), viewerId).map(PostVote::getValue).orElse(0);
        boolean following = viewerId != null && postFollowRepository.existsByUserIdAndPostId(viewerId, post.getId());
        List<String> commenters = postCommentRepository.findDistinctAuthorIdsByPostId(post.getId());
        Set<String> participantIds = new HashSet<>(commenters);
        participantIds.add(post.getAuthorId());
        Instant lastActivity = postCommentRepository.findLatestCommentTimeByPostIds(List.of(post.getId())).stream()
                .findFirst().map(row -> (Instant) row[1]).orElse(post.getCreatedAt());

        return new DiscussionDto(base.id(), base.authorId(), base.content(), base.visibility(), base.linkUrl(),
                post.getTopic() == null ? null : post.getTopic().name(),
                post.getTopic() == null ? Post.Topic.GENERAL.getLabel() : post.getTopic().getLabel(),
                tags, base.likesCount(), base.isLiked(), base.isSaved(), base.commentsCount(), netScore, myVote,
                views, participantIds.size(), following, base.commentsDisabled(), base.attachments(),
                base.removedByAdmin(), base.removalReason(), base.createdAt(), lastActivity);
    }

    /** Used only right after {@link #create}, where there's no PostDto yet to read isLiked/isSaved/etc. from
     *  (a brand-new discussion has none of those anyway). */
    private DiscussionDto toDto(Post post, boolean isLiked, boolean isSaved, boolean isFollowing, int netScore,
                                 int myVote, long views, int participantCount, List<String> tags, Instant lastActivity) {
        List<AttachmentDto> attachments = post.getAttachments().stream()
                .map(a -> new AttachmentDto(a.getId(), a.getUrl(), a.getKind().name(), a.getFileName())).toList();
        return new DiscussionDto(post.getId(), post.getAuthorId(), post.getContent(), post.getVisibility().name(), post.getLinkUrl(),
                post.getTopic() == null ? null : post.getTopic().name(),
                post.getTopic() == null ? Post.Topic.GENERAL.getLabel() : post.getTopic().getLabel(),
                tags, post.getLikesCount(), isLiked, isSaved, post.getCommentsCount(), netScore, myVote,
                views, participantCount, isFollowing, post.isCommentsDisabled(), attachments,
                post.isRemovedByAdmin(), post.getRemovalReason(), post.getCreatedAt(), lastActivity);
    }

    private DiscussionCommentDto toCommentDto(CommentDto c, int likesCount, boolean isLiked) {
        return new DiscussionCommentDto(c.id(), c.postId(), c.parentCommentId(), c.authorId(), c.content(),
                c.replyCount(), likesCount, isLiked, c.createdAt());
    }

    private Post.Topic parseTopicOrNull(String topic) {
        if (topic == null || topic.isBlank()) return null;
        try {
            return Post.Topic.valueOf(topic.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid topic: " + topic);
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

    private String normalizedTagOrNoMatch(String tag) {
        if (tag == null || tag.isBlank()) return null;
        String normalized = Hashtags.normalize(tag);
        return normalized != null ? normalized : NO_SUCH_TAG;
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.RECENT;
        try {
            return Sort.valueOf(sort.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid sort: " + sort);
        }
    }
}
