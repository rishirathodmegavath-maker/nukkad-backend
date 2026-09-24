package com.nukkad.feed.recommendation;

import com.nukkad.feed.entity.Post;
import com.nukkad.feed.repository.PostHideRepository;
import com.nukkad.feed.repository.PostRepository;
import com.nukkad.user.entity.Connection;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserBlockRepository;
import com.nukkad.user.repository.UserFollowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCandidatePoolServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private UserFollowRepository userFollowRepository;
    @Mock private ConnectionRepository connectionRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private PostHideRepository postHideRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private PostCandidatePoolService service() {
        return new PostCandidatePoolService(postRepository, userFollowRepository, connectionRepository,
                userBlockRepository, postHideRepository, clock);
    }

    private Post post(String id, String authorId) {
        return Post.builder().id(id).authorId(authorId).content("no tags").type(Post.Type.text)
                .createdAt(clock.instant()).build();
    }

    @Test
    void blockedAuthorsArePermanentlyExcludedEvenIfFollowed() {
        when(userFollowRepository.findFolloweeIdsByFollowerId("viewer")).thenReturn(List.of("blockedAuthor"));
        when(connectionRepository.findAcceptedConnections("viewer")).thenReturn(List.of());
        when(userBlockRepository.findBlockedEitherWayIds("viewer")).thenReturn(Set.of("blockedAuthor"));
        when(postHideRepository.findAllPostIdsByUserId("viewer")).thenReturn(Set.of());
        // The author-bucket query is never even called with a blocked-only author set (empty after removal).
        when(postRepository.findRecentGeneral(eq("viewer"), any(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(post("p1", "blockedAuthor")));

        List<Post> pool = service().gather("viewer", Map.of(), List.of());

        assertThat(pool).isEmpty();
        verify(postRepository, never()).findRecentByAuthors(any(), any(), any(), any(), any());
    }

    @Test
    void deduplicatesAPostReturnedByMultipleBuckets() {
        Post shared = post("p1", "author1");
        when(userFollowRepository.findFolloweeIdsByFollowerId("viewer")).thenReturn(List.of("author1"));
        when(connectionRepository.findAcceptedConnections("viewer")).thenReturn(List.of());
        when(userBlockRepository.findBlockedEitherWayIds("viewer")).thenReturn(Set.of());
        when(postHideRepository.findAllPostIdsByUserId("viewer")).thenReturn(Set.of());
        when(postRepository.findRecentByAuthors(eq("viewer"), anyCollection(), any(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(shared));
        when(postRepository.findRecentByTags(eq("viewer"), anyCollection(), any(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(shared));
        when(postRepository.findRecentGeneral(eq("viewer"), any(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(shared));

        List<Post> pool = service().gather("viewer", Map.of("HASHTAG:ai", 1.0), List.of());

        assertThat(pool).hasSize(1).containsExactly(shared);
    }

    @Test
    void aThinPoolTriggersAWindowlessFallbackQuery() {
        when(userFollowRepository.findFolloweeIdsByFollowerId("viewer")).thenReturn(List.of());
        when(connectionRepository.findAcceptedConnections("viewer")).thenReturn(List.of());
        when(userBlockRepository.findBlockedEitherWayIds("viewer")).thenReturn(Set.of());
        when(postHideRepository.findAllPostIdsByUserId("viewer")).thenReturn(Set.of());
        // Windowed general bucket returns almost nothing — below MIN_POOL_SIZE.
        when(postRepository.findRecentGeneral(eq("viewer"), any(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(post("p1", "someone")))
                .thenReturn(List.of(post("p1", "someone"), post("p2", "someone-else")));

        List<Post> pool = service().gather("viewer", Map.of(), List.of());

        // Called twice: once with the real window, once as the Instant.EPOCH fallback.
        verify(postRepository, times(2)).findRecentGeneral(eq("viewer"), any(), anyCollection(), any(Pageable.class));
        assertThat(pool).extracting(Post::getId).contains("p1", "p2");
    }

    @Test
    void clientExcludeIdsAreMergedWithHiddenPostIds() {
        when(userFollowRepository.findFolloweeIdsByFollowerId("viewer")).thenReturn(List.of());
        when(connectionRepository.findAcceptedConnections("viewer")).thenReturn(List.of());
        when(userBlockRepository.findBlockedEitherWayIds("viewer")).thenReturn(Set.of());
        when(postHideRepository.findAllPostIdsByUserId("viewer")).thenReturn(Set.of("hiddenPost"));
        when(postRepository.findRecentGeneral(eq("viewer"), any(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of());

        service().gather("viewer", Map.of(), List.of("clientShownPost"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> excludeCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(postRepository, times(2)).findRecentGeneral(eq("viewer"), any(), excludeCaptor.capture(), any(Pageable.class));
        assertThat(excludeCaptor.getAllValues().get(0)).contains("hiddenPost", "clientShownPost");
    }

    @Test
    void anAcceptedConnectionCountsAsAFollowedAuthorEvenWithoutAnExplicitFollow() {
        Connection connection = Connection.builder().userAId("viewer").userBId("connectedAuthor").build();
        when(userFollowRepository.findFolloweeIdsByFollowerId("viewer")).thenReturn(List.of());
        when(connectionRepository.findAcceptedConnections("viewer")).thenReturn(List.of(connection));
        when(userBlockRepository.findBlockedEitherWayIds("viewer")).thenReturn(Set.of());
        when(postHideRepository.findAllPostIdsByUserId("viewer")).thenReturn(Set.of());
        when(postRepository.findRecentByAuthors(eq("viewer"), eq(Set.of("connectedAuthor")), any(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(post("p1", "connectedAuthor")));
        when(postRepository.findRecentGeneral(eq("viewer"), any(), anyCollection(), any(Pageable.class))).thenReturn(List.of());

        List<Post> pool = service().gather("viewer", Map.of(), List.of());

        assertThat(pool).extracting(Post::getId).containsExactly("p1");
    }
}
