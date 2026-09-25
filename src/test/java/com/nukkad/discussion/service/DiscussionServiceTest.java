package com.nukkad.discussion.service;

import com.nukkad.discussion.entity.PostCommentLike;
import com.nukkad.discussion.entity.PostFollow;
import com.nukkad.discussion.entity.PostVote;
import com.nukkad.discussion.repository.PostCommentLikeRepository;
import com.nukkad.discussion.repository.PostFollowRepository;
import com.nukkad.discussion.repository.PostVoteRepository;
import com.nukkad.discussion.repository.PostViewRepository;
import com.nukkad.feed.dto.PostDto;
import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.PostComment;
import com.nukkad.feed.repository.PostCommentRepository;
import com.nukkad.feed.repository.PostHashtagRepository;
import com.nukkad.feed.repository.PostLikeRepository;
import com.nukkad.feed.repository.PostRepository;
import com.nukkad.feed.repository.PostSaveRepository;
import com.nukkad.feed.service.FeedService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscussionServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private PostHashtagRepository postHashtagRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostSaveRepository postSaveRepository;
    @Mock private PostViewRepository postViewRepository;
    @Mock private PostVoteRepository postVoteRepository;
    @Mock private PostFollowRepository postFollowRepository;
    @Mock private PostCommentLikeRepository postCommentLikeRepository;
    @Mock private FeedService feedService;

    private DiscussionService service() {
        return new DiscussionService(postRepository, postCommentRepository, postHashtagRepository, postLikeRepository,
                postSaveRepository, postViewRepository, postVoteRepository, postFollowRepository,
                postCommentLikeRepository, feedService);
    }

    private Post discussion(String id) {
        return Post.builder().id(id).authorId("author-1").type(Post.Type.discussion).content("hello").createdAt(Instant.now()).build();
    }

    private PostDto baseDto(Post post) {
        return new PostDto(post.getId(), post.getAuthorId(), "discussion", post.getContent(), null,
                0, 0, false, false, false, false, post.getCreatedAt(), List.of(), null, false, null, "PUBLIC", null, false,
                "BUILDADDA", 0);
    }

    // ---- voting ----

    @Test
    void anUpvoteOnAPostWithNoExistingVoteInsertsAPlusOne() {
        Post post = discussion("p1");
        when(postRepository.findById("p1")).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId("p1", "u1")).thenReturn(Optional.empty());
        when(feedService.get("u1", "p1")).thenReturn(baseDto(post));

        service().castVote("u1", "p1", "up");

        ArgumentCaptor<PostVote> saved = ArgumentCaptor.forClass(PostVote.class);
        verify(postVoteRepository).save(saved.capture());
        assertThat(saved.getValue().getValue()).isEqualTo(1);
    }

    @Test
    void votingTheSameDirectionAgainRemovesTheVoteInsteadOfDuplicatingIt() {
        Post post = discussion("p1");
        when(postRepository.findById("p1")).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId("p1", "u1"))
                .thenReturn(Optional.of(PostVote.builder().postId("p1").userId("u1").value(1).build()));
        when(feedService.get("u1", "p1")).thenReturn(baseDto(post));

        service().castVote("u1", "p1", "up");

        verify(postVoteRepository).deleteByPostIdAndUserId("p1", "u1");
        verify(postVoteRepository, never()).save(any());
    }

    @Test
    void votingTheOppositeDirectionSwitchesTheExistingVoteRatherThanAddingASecondRow() {
        Post post = discussion("p1");
        PostVote existing = PostVote.builder().postId("p1").userId("u1").value(1).build();
        when(postRepository.findById("p1")).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId("p1", "u1")).thenReturn(Optional.of(existing));
        when(feedService.get("u1", "p1")).thenReturn(baseDto(post));

        service().castVote("u1", "p1", "down");

        assertThat(existing.getValue()).isEqualTo(-1);
        verify(postVoteRepository).save(existing);
        verify(postVoteRepository, never()).deleteByPostIdAndUserId(any(), any());
    }

    // ---- following ----

    @Test
    void followingAnUnfollowedDiscussionInsertsExactlyOneRow() {
        when(postRepository.findById("p1")).thenReturn(Optional.of(discussion("p1")));
        when(postFollowRepository.existsByUserIdAndPostId("u1", "p1")).thenReturn(false);

        var result = service().toggleFollow("u1", "p1");

        assertThat(result.following()).isTrue();
        verify(postFollowRepository).save(any(PostFollow.class));
    }

    @Test
    void followingAnAlreadyFollowedDiscussionUnfollowsIt() {
        when(postRepository.findById("p1")).thenReturn(Optional.of(discussion("p1")));
        when(postFollowRepository.existsByUserIdAndPostId("u1", "p1")).thenReturn(true);

        var result = service().toggleFollow("u1", "p1");

        assertThat(result.following()).isFalse();
        verify(postFollowRepository).deleteByUserIdAndPostId("u1", "p1");
    }

    // ---- view recording ----

    @Test
    void recordViewSkipsTheDiscussionsOwnAuthorSoTheyCantInflateTheirOwnCount() {
        when(postRepository.findById("p1")).thenReturn(Optional.of(discussion("p1")));

        service().recordView("p1", "author-1");

        verify(postViewRepository, never()).save(any());
    }

    @Test
    void recordViewCountsAnAnonymousViewer() {
        when(postRepository.findById("p1")).thenReturn(Optional.of(discussion("p1")));

        service().recordView("p1", null);

        verify(postViewRepository).save(any());
    }

    @Test
    void recordViewCountsAViewerWhoIsNotTheAuthor() {
        when(postRepository.findById("p1")).thenReturn(Optional.of(discussion("p1")));

        service().recordView("p1", "someone-else");

        verify(postViewRepository).save(any());
    }

    // ---- topics ----

    @Test
    void listTopicsIncludesEveryCuratedTopicEvenOneNobodyHasUsedYet() {
        when(postRepository.countDiscussionsByTopic())
                .thenReturn(java.util.Collections.singletonList(new Object[] {Post.Topic.AI_TECHNOLOGY, 5L}));

        var topics = service().listTopics();

        assertThat(topics).extracting("topic").contains(
                "AI_TECHNOLOGY", "PRODUCT", "GROWTH", "FUNDRAISING", "COFOUNDERS",
                "MARKETING", "HIRING", "TOOLS_RESOURCES", "STARTUPS", "GENERAL");
        assertThat(topics).filteredOn(t -> t.topic().equals("AI_TECHNOLOGY")).extracting("count").containsExactly(5L);
        assertThat(topics).filteredOn(t -> t.topic().equals("GENERAL")).extracting("count").containsExactly(0L);
    }

    // ---- stats ----

    @Test
    void statsCountsAParticipantOnlyOnceEvenWhenTheyAuthoredAndReplied() {
        when(postRepository.countByTypeAndRemovedByAdminFalseAndVisibility(Post.Type.discussion, Post.Visibility.PUBLIC)).thenReturn(3L);
        when(postCommentRepository.countCommentsOnDiscussions()).thenReturn(10L);
        when(postRepository.findDistinctDiscussionAuthorIds()).thenReturn(List.of("author-1", "author-2"));
        when(postCommentRepository.findDistinctCommenterIdsOnDiscussions()).thenReturn(List.of("author-1", "commenter-1"));

        var stats = service().getStats();

        assertThat(stats.totalDiscussions()).isEqualTo(3);
        assertThat(stats.totalReplies()).isEqualTo(10);
        assertThat(stats.totalParticipants()).isEqualTo(3); // author-1, author-2, commenter-1 — not 4
    }

    // ---- comment likes ----

    @Test
    void likingAReplyWithNoExistingLikeInsertsAndIncrements() {
        Post post = discussion("p1");
        PostComment comment = PostComment.builder().id("c1").postId("p1").authorId("someone").content("hi").likesCount(0).build();
        when(feedService.get("u1", "p1")).thenReturn(baseDto(post));
        when(postCommentRepository.findById("c1")).thenReturn(Optional.of(comment), Optional.of(comment));
        when(postCommentLikeRepository.findByCommentIdAndUserId("c1", "u1")).thenReturn(Optional.empty());

        service().toggleCommentLike("u1", "p1", "c1");

        verify(postCommentLikeRepository).save(any(PostCommentLike.class));
        verify(postCommentRepository).incrementLikesCount("c1");
    }

    @Test
    void unlikingAnAlreadyLikedReplyDeletesAndDecrements() {
        Post post = discussion("p1");
        PostComment comment = PostComment.builder().id("c1").postId("p1").authorId("someone").content("hi").likesCount(1).build();
        when(feedService.get("u1", "p1")).thenReturn(baseDto(post));
        when(postCommentRepository.findById("c1")).thenReturn(Optional.of(comment), Optional.of(comment));
        when(postCommentLikeRepository.findByCommentIdAndUserId("c1", "u1"))
                .thenReturn(Optional.of(PostCommentLike.builder().id("l1").commentId("c1").userId("u1").build()));

        service().toggleCommentLike("u1", "p1", "c1");

        verify(postCommentLikeRepository).deleteByCommentIdAndUserId("c1", "u1");
        verify(postCommentRepository).decrementLikesCount("c1");
    }

    // ---- participants ----

    @Test
    void participantsListsTheAuthorFirstThenEachDistinctCommenterWithoutDuplicatingTheAuthor() {
        when(postRepository.findById("p1")).thenReturn(Optional.of(discussion("p1")));
        when(postCommentRepository.findDistinctAuthorIdsByPostId("p1")).thenReturn(List.of("author-1", "commenter-1", "commenter-2"));

        var participants = service().listParticipants("p1");

        assertThat(participants).containsExactly("author-1", "commenter-1", "commenter-2");
    }
}
