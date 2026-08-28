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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

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
            postLikeRepository.save(PostLike.builder().postId(postId).userId(viewerId).build());
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
        return postCommentRepository.findByPostIdOrderByCreatedAtAsc(postId, PageRequest.of(page, size))
                .map(this::toCommentDto);
    }

    @Transactional
    public CommentDto addComment(String authorId, String postId, CreateCommentRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        if (post.isCommentsDisabled()) {
            throw new BadRequestException("Comments are turned off for this post");
        }

        PostComment comment = postCommentRepository.save(PostComment.builder()
                .postId(postId)
                .authorId(authorId)
                .content(request.content().trim())
                .build());

        postRepository.incrementCommentsCount(postId);

        return toCommentDto(comment);
    }

    public AttachmentRef uploadAttachment(MultipartFile file, String publicBaseUrl) {
        var stored = fileStorageService.storeMedia(file, "feed");
        String originalName = file.getOriginalFilename();
        return new AttachmentRef(publicBaseUrl + "/uploads/" + stored.path(), stored.kind().name(), originalName);
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
        List<AttachmentDto> attachments = post.getAttachments().stream()
                .map(a -> new AttachmentDto(a.getId(), a.getUrl(), a.getKind().name(), a.getFileName()))
                .toList();
        return new PostDto(post.getId(), post.getAuthorId(), post.getType().name(), post.getContent(), post.getRelatedId(),
                post.getLikesCount(), post.getCommentsCount(), isLiked, isSaved, post.isHideLikeCount(), post.isCommentsDisabled(),
                post.getCreatedAt(), attachments);
    }

    private CommentDto toCommentDto(PostComment comment) {
        return new CommentDto(comment.getId(), comment.getPostId(), comment.getAuthorId(), comment.getContent(), comment.getCreatedAt());
    }
}
