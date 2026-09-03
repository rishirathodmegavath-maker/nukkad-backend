package com.nukkad.feed.service;

import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.feed.dto.CreateCommentRequest;
import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.PostComment;
import com.nukkad.feed.entity.PostLike;
import com.nukkad.feed.repository.PostCommentRepository;
import com.nukkad.feed.repository.PostLikeRepository;
import com.nukkad.feed.repository.PostRepository;
import com.nukkad.feed.repository.PostSaveRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the like toggle's exactly-once semantics: create on first like, remove on second, never a duplicate row. */
@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private PostSaveRepository postSaveRepository;
    @Mock private FileStorageService fileStorageService;

    private FeedService service() {
        return new FeedService(postRepository, postLikeRepository, postCommentRepository, postSaveRepository, fileStorageService);
    }

    private Post post(String id) {
        return Post.builder().id(id).authorId("author-1").content("hello").likesCount(0).build();
    }

    @Test
    void likingAnUnlikedPostInsertsExactlyOneRowAndIncrementsCount() {
        Post post = post("post-1");
        when(postRepository.existsById("post-1")).thenReturn(true);
        when(postLikeRepository.findByPostIdAndUserId("post-1", "user-1")).thenReturn(Optional.empty());
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postSaveRepository.findByPostIdAndUserId("post-1", "user-1")).thenReturn(Optional.empty());

        var dto = service().toggleLike("user-1", "post-1");

        assertThat(dto.isLiked()).isTrue();
        ArgumentCaptor<PostLike> captor = ArgumentCaptor.forClass(PostLike.class);
        verify(postLikeRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo("post-1");
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
        verify(postLikeRepository, never()).deleteByPostIdAndUserId(any(), any());
        verify(postRepository).incrementLikesCount("post-1");
        verify(postRepository, never()).decrementLikesCount(any());
    }

    @Test
    void togglingAnAlreadyLikedPostRemovesItInsteadOfInsertingAgain() {
        Post post = post("post-1");
        when(postRepository.existsById("post-1")).thenReturn(true);
        when(postLikeRepository.findByPostIdAndUserId("post-1", "user-1"))
                .thenReturn(Optional.of(PostLike.builder().id("like-1").postId("post-1").userId("user-1").build()));
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postSaveRepository.findByPostIdAndUserId("post-1", "user-1")).thenReturn(Optional.empty());

        var dto = service().toggleLike("user-1", "post-1");

        assertThat(dto.isLiked()).isFalse();
        verify(postLikeRepository).deleteByPostIdAndUserId("post-1", "user-1");
        verify(postLikeRepository, never()).saveAndFlush(any());
        verify(postRepository).decrementLikesCount("post-1");
        verify(postRepository, never()).incrementLikesCount(any());
    }

    @Test
    void likingANonexistentPostIsNotFound() {
        when(postRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service().toggleLike("user-1", "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(postLikeRepository, never()).saveAndFlush(any());
        verify(postLikeRepository, never()).deleteByPostIdAndUserId(any(), any());
    }

    @Test
    void twoDifferentUsersLikingTheSamePostEachGetTheirOwnIndependentLikeRow() {
        Post post = post("post-1");
        when(postRepository.existsById("post-1")).thenReturn(true);
        when(postLikeRepository.findByPostIdAndUserId(eq("post-1"), any())).thenReturn(Optional.empty());
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postSaveRepository.findByPostIdAndUserId(eq("post-1"), any())).thenReturn(Optional.empty());

        var aliceDto = service().toggleLike("alice", "post-1");
        var bobDto = service().toggleLike("bob", "post-1");

        assertThat(aliceDto.isLiked()).isTrue();
        assertThat(bobDto.isLiked()).isTrue();
        verify(postLikeRepository).findByPostIdAndUserId("post-1", "alice");
        verify(postLikeRepository).findByPostIdAndUserId("post-1", "bob");
        verify(postRepository, org.mockito.Mockito.times(2)).incrementLikesCount("post-1");
    }

    private PostComment comment(String id, String postId, String parentId, String authorId) {
        return PostComment.builder().id(id).postId(postId).parentCommentId(parentId).authorId(authorId).content("hi").build();
    }

    @Test
    void replyingToAReplyFlattensOntoTheOriginalTopLevelComment() {
        Post post = post("post-1");
        PostComment reply = comment("reply-1", "post-1", "top-1", "user-2");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postCommentRepository.findById("reply-1")).thenReturn(Optional.of(reply));
        when(postCommentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().addComment("user-3", "post-1", new CreateCommentRequest("nested reply", "reply-1"));

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getParentCommentId()).isEqualTo("top-1");
    }

    @Test
    void replyingToATopLevelCommentAttachesDirectly() {
        Post post = post("post-1");
        PostComment top = comment("top-1", "post-1", null, "user-2");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postCommentRepository.findById("top-1")).thenReturn(Optional.of(top));
        when(postCommentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().addComment("user-3", "post-1", new CreateCommentRequest("a reply", "top-1"));

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getParentCommentId()).isEqualTo("top-1");
    }

    @Test
    void commentAuthorCanDeleteTheirOwnComment() {
        Post post = post("post-1");
        PostComment top = comment("top-1", "post-1", null, "commenter-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postCommentRepository.findById("top-1")).thenReturn(Optional.of(top));
        when(postCommentRepository.countByParentCommentId("top-1")).thenReturn(2L);

        service().deleteComment("commenter-1", "post-1", "top-1");

        verify(postCommentRepository).delete(top);
        verify(postRepository).decrementCommentsCount("post-1", 3);
    }

    @Test
    void postAuthorCanDeleteSomeoneElsesComment() {
        Post post = post("post-1"); // authorId = "author-1"
        PostComment top = comment("top-1", "post-1", null, "commenter-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postCommentRepository.findById("top-1")).thenReturn(Optional.of(top));
        when(postCommentRepository.countByParentCommentId("top-1")).thenReturn(0L);

        service().deleteComment("author-1", "post-1", "top-1");

        verify(postCommentRepository).delete(top);
        verify(postRepository).decrementCommentsCount("post-1", 1);
    }

    @Test
    void aThirdPartyCannotDeleteSomeoneElsesComment() {
        Post post = post("post-1"); // authorId = "author-1"
        PostComment top = comment("top-1", "post-1", null, "commenter-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postCommentRepository.findById("top-1")).thenReturn(Optional.of(top));

        assertThatThrownBy(() -> service().deleteComment("random-user", "post-1", "top-1"))
                .isInstanceOf(ForbiddenException.class);
        verify(postCommentRepository, never()).delete(any());
        verify(postRepository, never()).decrementCommentsCount(any(), any(Integer.class));
    }

    @Test
    void deletingAReplyDoesNotCountRepliesOfItsOwn() {
        Post post = post("post-1");
        PostComment reply = comment("reply-1", "post-1", "top-1", "commenter-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postCommentRepository.findById("reply-1")).thenReturn(Optional.of(reply));

        service().deleteComment("commenter-1", "post-1", "reply-1");

        verify(postCommentRepository, never()).countByParentCommentId(any());
        verify(postRepository).decrementCommentsCount("post-1", 1);
    }

    @Test
    void listLikersReturnsAPaginatedPageOfLikerRows() {
        when(postRepository.existsById("post-1")).thenReturn(true);
        PostLike like = PostLike.builder().id("like-1").postId("post-1").userId("user-1").build();
        when(postLikeRepository.findByPostIdOrderByCreatedAtDesc(eq("post-1"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(like)));

        var page = service().listLikers("post-1", 0, 50);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).userId()).isEqualTo("user-1");
    }

    @Test
    void listLikersOnANonexistentPostIsNotFound() {
        when(postRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service().listLikers("missing", 0, 50))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
