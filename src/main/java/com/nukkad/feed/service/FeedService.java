package com.nukkad.feed.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
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
import com.nukkad.feed.entity.PostLike;
import com.nukkad.feed.entity.PostSave;
import com.nukkad.feed.repository.PostCommentRepository;
import com.nukkad.feed.repository.PostLikeRepository;
import com.nukkad.feed.repository.PostRepository;
import com.nukkad.feed.repository.PostSaveRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FeedService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostSaveRepository postSaveRepository;
    private final FileStorageService fileStorageService;

    public FeedService(PostRepository postRepository, PostLikeRepository postLikeRepository,
                        PostCommentRepository postCommentRepository, PostSaveRepository postSaveRepository,
                        FileStorageService fileStorageService) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.postCommentRepository = postCommentRepository;
        this.postSaveRepository = postSaveRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public Page<PostDto> list(String viewerId, String authorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> posts = (authorId == null || authorId.isBlank())
                ? postRepository.findAllByOrderByCreatedAtDesc(pageable)
                : postRepository.findByAuthorIdOrderByCreatedAtDesc(authorId, pageable);

        List<String> postIds = posts.getContent().stream().map(Post::getId).toList();
        Set<String> likedIds = postIds.isEmpty() ? Set.of() : postLikeRepository.findLikedPostIds(viewerId, postIds);
        Set<String> savedIds = postIds.isEmpty() ? Set.of() : postSaveRepository.findSavedPostIds(viewerId, postIds);

        return posts.map(p -> toDto(p, likedIds.contains(p.getId()), savedIds.contains(p.getId())));
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
        Pageable pageable = PageRequest.of(page, size);

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
        String content = request.content() == null ? "" : request.content().trim();
        List<AttachmentRef> attachmentRefs = request.attachments() == null ? List.of() : request.attachments();
        if (content.isEmpty() && attachmentRefs.isEmpty()) {
            throw new BadRequestException("A post needs text or at least one attachment");
        }

        Post.Type type = parseType(request.type());

        Post post = Post.builder()
                .authorId(authorId)
                .type(type)
                .content(content)
                .relatedId(request.relatedId())
                .build();

        for (int i = 0; i < attachmentRefs.size(); i++) {
            AttachmentRef ref = attachmentRefs.get(i);
            post.getAttachments().add(PostAttachment.builder()
                    .post(post)
                    .url(ref.url())
                    .kind(parseKind(ref.kind()))
                    .fileName(ref.fileName())
                    .sortOrder(i)
                    .build());
        }

        return toDto(postRepository.save(post), false, false);
    }

    @Transactional
    public PostDto toggleLike(String viewerId, String postId) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post not found: " + postId);
        }

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
        boolean saved = postSaveRepository.findByPostIdAndUserId(postId, viewerId).isPresent();
        return toDto(refreshed, liked, saved);
    }

    @Transactional
    public PostDto toggleSave(String viewerId, String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        var existing = postSaveRepository.findByPostIdAndUserId(postId, viewerId);
        boolean saved;
        if (existing.isPresent()) {
            postSaveRepository.deleteByPostIdAndUserId(postId, viewerId);
            saved = false;
        } else {
            postSaveRepository.save(PostSave.builder().postId(postId).userId(viewerId).build());
            saved = true;
        }
        boolean liked = postLikeRepository.findByPostIdAndUserId(postId, viewerId).isPresent();
        return toDto(post, liked, saved);
    }

    @Transactional(readOnly = true)
    public PostDto get(String viewerId, String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        return toDto(post, viewerId);
    }

    @Transactional
    public void delete(String viewerId, String postId) {
        Post post = requireOwnedPost(viewerId, postId);
        postRepository.delete(post);
    }

    @Transactional
    public PostDto update(String viewerId, String postId, UpdatePostRequest request) {
        Post post = requireOwnedPost(viewerId, postId);
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty() && post.getAttachments().isEmpty()) {
            throw new BadRequestException("A post needs text or at least one attachment");
        }
        post.setContent(content);
        postRepository.save(post);
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

    private Post requireOwnedPost(String viewerId, String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        if (!post.getAuthorId().equals(viewerId)) {
            throw new ForbiddenException("You can only manage your own posts");
        }
        return post;
    }

    @Transactional(readOnly = true)
    public Page<CommentDto> listComments(String postId, int page, int size) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post not found: " + postId);
        }
        Page<PostComment> comments = postCommentRepository
                .findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(postId, PageRequest.of(page, size));

        List<String> ids = comments.getContent().stream().map(PostComment::getId).toList();
        Map<String, Integer> replyCounts = ids.isEmpty() ? Map.of() : postCommentRepository
                .countRepliesGroupedByParent(ids).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Long) row[1]).intValue()));

        return comments.map(c -> toCommentDto(c, replyCounts.getOrDefault(c.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public Page<CommentDto> listReplies(String postId, String commentId, int page, int size) {
        PostComment parent = postCommentRepository.findById(commentId)
                .filter(c -> c.getPostId().equals(postId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        if (parent.getParentCommentId() != null) {
            throw new BadRequestException("Cannot list replies of a reply");
        }
        return postCommentRepository.findByParentCommentIdOrderByCreatedAtAsc(commentId, PageRequest.of(page, size))
                .map(c -> toCommentDto(c, 0));
    }

    @Transactional
    public CommentDto addComment(String authorId, String postId, CreateCommentRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
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
    public Page<PostLikeDto> listLikers(String postId, int page, int size) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post not found: " + postId);
        }
        return postLikeRepository.findByPostIdOrderByCreatedAtDesc(postId, PageRequest.of(page, size))
                .map(l -> new PostLikeDto(l.getUserId(), l.getCreatedAt()));
    }

    public AttachmentRef uploadAttachment(MultipartFile file) {
        var stored = fileStorageService.storeMedia(file, "feed");
        String originalName = file.getOriginalFilename();
        return new AttachmentRef(stored.url(), stored.kind().name(), originalName);
    }

    private Post.Type parseType(String type) {
        if (type == null || type.isBlank()) return Post.Type.text;
        try {
            return Post.Type.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid post type: " + type);
        }
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
                .map(a -> new AttachmentDto(a.getId(), a.getUrl(), a.getKind().name(), a.getFileName()))
                .toList();
        return new PostDto(post.getId(), post.getAuthorId(), post.getType().name(), post.getContent(), post.getRelatedId(),
                post.getLikesCount(), post.getCommentsCount(), isLiked, isSaved, post.isHideLikeCount(), post.isCommentsDisabled(),
                post.getCreatedAt(), attachments, savedAt);
    }

    private CommentDto toCommentDto(PostComment comment, int replyCount) {
        return new CommentDto(comment.getId(), comment.getPostId(), comment.getParentCommentId(), comment.getAuthorId(),
                comment.getContent(), replyCount, comment.getCreatedAt());
    }
}
