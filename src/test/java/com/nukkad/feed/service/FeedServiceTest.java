package com.nukkad.feed.service;

import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.feed.dto.CreatePostRequest;
import com.nukkad.feed.dto.PostDto;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.feed.dto.CreateCommentRequest;
import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.PostComment;
import com.nukkad.feed.entity.PostLike;
import com.nukkad.feed.entity.PostSave;
import com.nukkad.feed.repository.PostCommentRepository;
import com.nukkad.feed.repository.PostLikeRepository;
import com.nukkad.feed.repository.PostRepository;
import com.nukkad.feed.repository.PostSaveRepository;
import com.nukkad.user.repository.ConnectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock private AuditService auditService;
    @Mock private ConnectionRepository connectionRepository;

    private FeedService service() {
        return new FeedService(postRepository, postLikeRepository, postCommentRepository, postSaveRepository,
                fileStorageService, auditService, connectionRepository);
    }

    private Post post(String id) {
        return Post.builder().id(id).authorId("author-1").content("hello").likesCount(0).build();
    }

    @Test
    void likingAnUnlikedPostInsertsExactlyOneRowAndIncrementsCount() {
        Post post = post("post-1");
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
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().toggleLike("user-1", "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(postLikeRepository, never()).saveAndFlush(any());
        verify(postLikeRepository, never()).deleteByPostIdAndUserId(any(), any());
    }

    @Test
    void twoDifferentUsersLikingTheSamePostEachGetTheirOwnIndependentLikeRow() {
        Post post = post("post-1");
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
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post("post-1")));
        PostLike like = PostLike.builder().id("like-1").postId("post-1").userId("user-1").build();
        when(postLikeRepository.findByPostIdOrderByCreatedAtDesc(eq("post-1"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(like)));

        var page = service().listLikers("user-1", "post-1", 0, 50);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).userId()).isEqualTo("user-1");
    }

    @Test
    void listLikersOnANonexistentPostIsNotFound() {
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().listLikers("user-1", "missing", 0, 50))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PostSave save(String id, String postId, String userId, Instant createdAt) {
        return PostSave.builder().id(id).postId(postId).userId(userId).createdAt(createdAt).build();
    }

    @Test
    void listSavedDefaultsToNewestSavedAndQueriesOnlyTheCurrentUsersSaves() {
        Pageable pageable = PageRequest.of(0, 20);
        PostSave s1 = save("save-1", "post-1", "user-1", Instant.parse("2026-01-02T00:00:00Z"));
        when(postSaveRepository.findByUserOrderBySavedAtDesc(eq("user-1"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(s1), pageable, 1));
        when(postRepository.findAllById(List.of("post-1"))).thenReturn(List.of(post("post-1")));
        when(postLikeRepository.findLikedPostIds("user-1", List.of("post-1"))).thenReturn(Set.of());

        var page = service().listSaved("user-1", null, null, 0, 20);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).id()).isEqualTo("post-1");
        assertThat(page.getContent().get(0).isSaved()).isTrue();
        assertThat(page.getContent().get(0).savedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        verify(postSaveRepository).findByUserOrderBySavedAtDesc(eq("user-1"), isNull(), any());
        verify(postSaveRepository, never()).findByUserOrderBySavedAtAsc(any(), any(), any());
    }

    @Test
    void listSavedOldestSavedUsesTheAscendingSavedAtQuery() {
        when(postSaveRepository.findByUserOrderBySavedAtAsc(eq("user-1"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service().listSaved("user-1", null, "oldestSaved", 0, 20);

        verify(postSaveRepository).findByUserOrderBySavedAtAsc(eq("user-1"), isNull(), any());
    }

    @Test
    void listSavedNewestPostUsesThePostCreatedAtDescendingQuery() {
        when(postSaveRepository.findByUserOrderByPostCreatedAtDesc(eq("user-1"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service().listSaved("user-1", null, "newestPost", 0, 20);

        verify(postSaveRepository).findByUserOrderByPostCreatedAtDesc(eq("user-1"), isNull(), any());
    }

    @Test
    void listSavedOldestPostUsesThePostCreatedAtAscendingQuery() {
        when(postSaveRepository.findByUserOrderByPostCreatedAtAsc(eq("user-1"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service().listSaved("user-1", null, "oldestPost", 0, 20);

        verify(postSaveRepository).findByUserOrderByPostCreatedAtAsc(eq("user-1"), isNull(), any());
    }

    @Test
    void listSavedWithAnInvalidSortValueIsRejected() {
        assertThatThrownBy(() -> service().listSaved("user-1", null, "banana", 0, 20))
                .isInstanceOf(BadRequestException.class);
        verify(postSaveRepository, never()).findByUserOrderBySavedAtDesc(any(), any(), any());
    }

    @Test
    void listSavedPassesThePostTypeFilterThrough() {
        when(postSaveRepository.findByUserOrderBySavedAtDesc(eq("user-1"), eq(Post.Type.idea), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service().listSaved("user-1", "idea", null, 0, 20);

        verify(postSaveRepository).findByUserOrderBySavedAtDesc(eq("user-1"), eq(Post.Type.idea), any());
    }

    @Test
    void listSavedWithAnInvalidTypeFilterIsRejected() {
        assertThatThrownBy(() -> service().listSaved("user-1", "not-a-real-type", null, 0, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listSavedWithNoSavesReturnsAnEmptyPageNotAnError() {
        when(postSaveRepository.findByUserOrderBySavedAtDesc(eq("user-1"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var page = service().listSaved("user-1", null, null, 0, 20);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verify(postRepository, never()).findAllById(any());
    }

    @Test
    void listSavedPreservesTheRepositorysOrderingWhenReassemblingPostsFromTheBatchFetch() {
        // findAllById does not guarantee it returns rows in the requested id order — the service must
        // reassemble by walking the already-ordered save rows, not by trusting the batch-fetch's order.
        PostSave s1 = save("save-1", "post-1", "user-1", Instant.parse("2026-01-03T00:00:00Z"));
        PostSave s2 = save("save-2", "post-2", "user-1", Instant.parse("2026-01-02T00:00:00Z"));
        PostSave s3 = save("save-3", "post-3", "user-1", Instant.parse("2026-01-01T00:00:00Z"));
        when(postSaveRepository.findByUserOrderBySavedAtDesc(eq("user-1"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(s1, s2, s3)));
        // Deliberately returned out of order to prove the service doesn't just trust this list's order.
        when(postRepository.findAllById(List.of("post-1", "post-2", "post-3")))
                .thenReturn(List.of(post("post-3"), post("post-1"), post("post-2")));
        when(postLikeRepository.findLikedPostIds(eq("user-1"), any())).thenReturn(Set.of());

        var page = service().listSaved("user-1", null, null, 0, 20);

        assertThat(page.getContent()).extracting("id").containsExactly("post-1", "post-2", "post-3");
    }

    @Test
    void listSavedSkipsAPostThatNoLongerExistsWithoutCrashing() {
        // Guards the (structurally near-impossible, given post_saves' ON DELETE CASCADE) race where a
        // post is removed between the save-rows query and the batch post fetch a moment later.
        PostSave s1 = save("save-1", "post-1", "user-1", Instant.now());
        PostSave s2 = save("save-2", "post-2", "user-1", Instant.now());
        when(postSaveRepository.findByUserOrderBySavedAtDesc(eq("user-1"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(s1, s2)));
        when(postRepository.findAllById(List.of("post-1", "post-2"))).thenReturn(List.of(post("post-1")));
        when(postLikeRepository.findLikedPostIds(eq("user-1"), any())).thenReturn(Set.of());

        var page = service().listSaved("user-1", null, null, 0, 20);

        assertThat(page.getContent()).extracting("id").containsExactly("post-1");
    }

    @Test
    void gettingARemovedPostIsNotFoundForEveryoneIncludingItsAuthor() {
        Post post = post("post-1");
        post.setRemovedByAdmin(true);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service().get("author-1", "post-1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publicListingAsksForOnlyWhatTheViewerMayRead() {
        when(postRepository.findVisibleTo(eq("user-1"), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service().list("user-1", null, null, 0, 20);

        verify(postRepository).findVisibleTo(eq("user-1"), isNull(), isNull(), any());
        // The admin listings ignore visibility and must never be what a member's feed uses.
        verify(postRepository, never()).findAllByOrderByCreatedAtDesc(any());
        verify(postRepository, never()).findByRemovedByAdminFalseOrderByCreatedAtDesc(any());
    }

    @Test
    void listingByTypePassesTheTypeToTheVisibleQuery() {
        when(postRepository.findVisibleTo(eq("user-1"), isNull(), eq(Post.Type.discussion), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service().list("user-1", null, "discussion", 0, 20);

        verify(postRepository).findVisibleTo(eq("user-1"), isNull(), eq(Post.Type.discussion), any());
    }

    @Test
    void listingByAuthorAndTypeCombinesBothFilters() {
        when(postRepository.findVisibleTo(eq("user-1"), eq("a1"), eq(Post.Type.question), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service().list("user-1", "a1", "question", 0, 20);

        verify(postRepository).findVisibleTo(eq("user-1"), eq("a1"), eq(Post.Type.question), any());
    }

    @Test
    void aBlankAuthorFilterMeansEveryone() {
        when(postRepository.findVisibleTo(eq("user-1"), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service().list("user-1", "  ", "", 0, 20);

        verify(postRepository).findVisibleTo(eq("user-1"), isNull(), isNull(), any());
    }

    @Test
    void listingWithAnUnknownTypeIsRejected() {
        assertThatThrownBy(() -> service().list("user-1", null, "gossip", 0, 20)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aMemberCanWriteEachOfTheNewPostKinds() {
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        for (String kind : new String[]{"discussion", "build_update", "question", "milestone",
                "idea", "feedback", "cofounder", "announcement", "resource", "hiring", "fundraising", "product_launch", "event"}) {
            PostDto dto = service().create("author-1", request("Shipped the beta", kind, null, null));
            assertThat(dto.type()).isEqualTo(kind);
        }
    }

    @Test
    void creatingAPostWithAnUnknownKindIsRejected() {
        assertThatThrownBy(() -> service().create("author-1", request("hi", "gossip", null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void removingAPostLogsAuditAndRestoringReversesIt() {
        Post post = post("post-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var removed = service().setRemovedByAdmin("admin-1", "post-1", true, "spam", "127.0.0.1");
        assertThat(removed.removedByAdmin()).isTrue();
        assertThat(removed.removalReason()).isEqualTo("spam");
        verify(auditService).log(eq("admin-1"), any(), eq("Post"), eq("post-1"), any(), any());

        var restored = service().setRemovedByAdmin("admin-1", "post-1", false, null, "127.0.0.1");
        assertThat(restored.removedByAdmin()).isFalse();
        assertThat(restored.removalReason()).isNull();
    }

    private CreatePostRequest request(String content, String type, String visibility, String linkUrl) {
        return new CreatePostRequest(content, type, null, null, visibility, linkUrl);
    }

    private Post connectionsOnlyPost() {
        return Post.builder().id("post-c").authorId("author-1").content("for my network")
                .visibility(Post.Visibility.CONNECTIONS).build();
    }

    // ---- visibility on create

    @Test
    void aPostIsPublicUnlessTheAuthorChoosesOtherwise() {
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service().create("author-1", request("hi", "text", null, null)).visibility()).isEqualTo("PUBLIC");
        assertThat(service().create("author-1", request("hi", "text", "", null)).visibility()).isEqualTo("PUBLIC");
        assertThat(service().create("author-1", request("hi", "text", "connections", null)).visibility()).isEqualTo("CONNECTIONS");
        assertThat(service().create("author-1", request("hi", "text", "CONNECTIONS", null)).visibility()).isEqualTo("CONNECTIONS");
    }

    @Test
    void anUnknownVisibilityIsRejected() {
        assertThatThrownBy(() -> service().create("author-1", request("hi", "text", "friends-of-friends", null)))
                .isInstanceOf(BadRequestException.class);
        verify(postRepository, never()).save(any());
    }

    // ---- links

    @Test
    void aLinkIsTrimmedAndKept() {
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostDto dto = service().create("author-1", request("worth a read", "resource", null, "  https://example.com/a?b=1  "));

        assertThat(dto.linkUrl()).isEqualTo("https://example.com/a?b=1");
    }

    @Test
    void aPostMayBeJustALink() {
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostDto dto = service().create("author-1", request("", "resource", null, "https://example.com"));

        assertThat(dto.content()).isEmpty();
        assertThat(dto.linkUrl()).isEqualTo("https://example.com");
    }

    @Test
    void aBlankLinkMeansNoLinkAndAnEmptyPostIsStillRejected() {
        assertThatThrownBy(() -> service().create("author-1", request("  ", "text", null, "   ")))
                .isInstanceOf(BadRequestException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void onlyWebLinksAreAccepted() {
        for (String bad : new String[]{"javascript:alert(1)", "ftp://example.com/file", "data:text/html,hi", "example.com",
                "https://", "//example.com", "not a link", "https://exa mple.com"}) {
            assertThatThrownBy(() -> service().create("author-1", request("x", "text", null, bad)))
                    .as(bad).isInstanceOf(BadRequestException.class);
        }
        verify(postRepository, never()).save(any());
    }

    @Test
    void aLinkOver500CharactersIsRejected() {
        String tooLong = "https://example.com/" + "a".repeat(490);
        assertThatThrownBy(() -> service().create("author-1", request("x", "text", null, tooLong)))
                .isInstanceOf(BadRequestException.class);
    }

    // ---- who can read a connections-only post

    @Test
    void theAuthorCanAlwaysReadTheirConnectionsOnlyPost() {
        when(postRepository.findById("post-c")).thenReturn(Optional.of(connectionsOnlyPost()));

        assertThat(service().get("author-1", "post-c").id()).isEqualTo("post-c");
        verify(connectionRepository, never()).existsAcceptedBetween(any(), any());
    }

    @Test
    void anAcceptedConnectionCanReadAConnectionsOnlyPost() {
        when(postRepository.findById("post-c")).thenReturn(Optional.of(connectionsOnlyPost()));
        when(connectionRepository.existsAcceptedBetween("author-1", "friend-1")).thenReturn(true);

        assertThat(service().get("friend-1", "post-c").visibility()).isEqualTo("CONNECTIONS");
    }

    @Test
    void aStrangerGetsNotFoundForAConnectionsOnlyPost() {
        when(postRepository.findById("post-c")).thenReturn(Optional.of(connectionsOnlyPost()));
        when(connectionRepository.existsAcceptedBetween("author-1", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> service().get("stranger", "post-c")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aPublicPostNeedsNoConnectionCheck() {
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post("post-1")));

        assertThat(service().get("stranger", "post-1").visibility()).isEqualTo("PUBLIC");
        verify(connectionRepository, never()).existsAcceptedBetween(any(), any());
    }

    @Test
    void aStrangerCannotLikeSaveCommentOrListLikersOfAConnectionsOnlyPost() {
        when(postRepository.findById("post-c")).thenReturn(Optional.of(connectionsOnlyPost()));
        when(connectionRepository.existsAcceptedBetween("author-1", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> service().toggleLike("stranger", "post-c")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().toggleSave("stranger", "post-c")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().addComment("stranger", "post-c", new CreateCommentRequest("hi", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().listComments("stranger", "post-c", 0, 50)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().listReplies("stranger", "post-c", "c1", 0, 50)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().listLikers("stranger", "post-c", 0, 50)).isInstanceOf(ResourceNotFoundException.class);

        verify(postLikeRepository, never()).saveAndFlush(any());
        verify(postSaveRepository, never()).save(any());
        verify(postCommentRepository, never()).saveAndFlush(any());
        verify(postRepository, never()).incrementLikesCount(any());
        verify(postRepository, never()).incrementCommentsCount(any());
    }

    @Test
    void aConnectionCanInteractWithAConnectionsOnlyPost() {
        when(postRepository.findById("post-c")).thenReturn(Optional.of(connectionsOnlyPost()));
        when(connectionRepository.existsAcceptedBetween("author-1", "friend-1")).thenReturn(true);
        when(postCommentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var comment = service().addComment("friend-1", "post-c", new CreateCommentRequest("nice", null));

        assertThat(comment.content()).isEqualTo("nice");
        verify(postRepository).incrementCommentsCount("post-c");
    }

    // ---- editing

    @Test
    void anAuthorMayClearTheTextOfAPostThatHasALink() {
        Post post = Post.builder().id("post-l").authorId("author-1").content("caption").linkUrl("https://example.com").build();
        when(postRepository.findById("post-l")).thenReturn(Optional.of(post));
        when(postLikeRepository.findByPostIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(postSaveRepository.findByPostIdAndUserId(any(), any())).thenReturn(Optional.empty());

        PostDto dto = service().update("author-1", "post-l", new com.nukkad.feed.dto.UpdatePostRequest(""));

        assertThat(dto.content()).isEmpty();
        assertThat(dto.linkUrl()).isEqualTo("https://example.com");
    }
}
