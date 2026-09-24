package com.nukkad.feed.recommendation;

import com.nukkad.feed.dto.PersonalizedFeedResultDto;
import com.nukkad.feed.entity.Post;
import com.nukkad.feed.repository.PostHashtagRepository;
import com.nukkad.feed.repository.PostInteractionRepository;
import com.nukkad.feed.service.FeedService;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserFollowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalizedFeedServiceTest {

    @Mock private PostCandidatePoolService candidatePoolService;
    @Mock private UserTopicAffinityService affinityService;
    @Mock private PostHashtagRepository postHashtagRepository;
    @Mock private PostInteractionRepository postInteractionRepository;
    @Mock private UserFollowRepository userFollowRepository;
    @Mock private ConnectionRepository connectionRepository;
    @Mock private FeedService feedService;

    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private PersonalizedFeedService service() {
        return new PersonalizedFeedService(candidatePoolService, affinityService, postHashtagRepository,
                postInteractionRepository, userFollowRepository, connectionRepository, feedService, clock);
    }

    private Post post(String id, String authorId, int likes, int comments) {
        return Post.builder().id(id).authorId(authorId).content("x").type(Post.Type.text)
                .likesCount(likes).commentsCount(comments).createdAt(now.minus(Duration.ofHours(1))).build();
    }

    /** Every batched lookup PersonalizedFeedService makes, defaulted to "nothing" — call this and
     *  then override only the specific stub(s) a test actually needs, to avoid Mockito's strict
     *  stubbing flagging an unused default in a test that doesn't reach that code path. */
    private void stubEmptyDefaults() {
        when(userFollowRepository.findFolloweeIdsByFollowerId("viewer")).thenReturn(List.of());
        when(connectionRepository.findAcceptedConnections("viewer")).thenReturn(List.of());
        when(postInteractionRepository.countPositiveInteractionsByAuthor(eq("viewer"), anyCollection())).thenReturn(List.of());
        when(feedService.toDtoList(any(), eq("viewer"))).thenReturn(List.of());
    }

    @Test
    void anEmptyCandidatePoolReturnsAnEmptyResultAndNeverCallsToDtoList() {
        when(affinityService.affinityMap("viewer")).thenReturn(Map.of());
        when(candidatePoolService.gather(eq("viewer"), any(), any())).thenReturn(List.of());

        PersonalizedFeedResultDto result = service().list("viewer", 10, List.of());

        assertThat(result.content()).isEmpty();
        assertThat(result.hasMore()).isFalse();
        org.mockito.Mockito.verify(feedService, org.mockito.Mockito.never()).toDtoList(any(), any());
    }

    @Test
    void aTrendingTagOutranksAnOtherwiseIdenticalNonTrendingPost() {
        stubEmptyDefaults();
        Post trendy = post("trendy", "author1", 0, 0);
        Post quiet = post("quiet", "author2", 0, 0);
        when(affinityService.affinityMap("viewer")).thenReturn(Map.of());
        when(candidatePoolService.gather(eq("viewer"), any(), any())).thenReturn(List.of(trendy, quiet));
        when(postHashtagRepository.findTagsByPostIds(anyCollection())).thenReturn(List.of(
                new Object[] {"trendy", "trendytag"}, new Object[] {"quiet", "quiettag"}));
        when(postHashtagRepository.findTrending(eq("viewer"), any(), any(Pageable.class)))
                .thenReturn(List.of(tagCount("trendytag", 100)));
        when(postInteractionRepository.findRecentlyInteractedPostIds(eq("viewer"), anyCollection(), any())).thenReturn(Set.of());

        service().list("viewer", 1, List.of());

        ArgumentCaptor<List<Post>> selected = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(feedService).toDtoList(selected.capture(), eq("viewer"));
        assertThat(selected.getValue()).extracting(Post::getId).containsExactly("trendy");
    }

    @Test
    void explorationReservesASlotForANovelPostEvenWhenOutscoredOnEveryOtherFactor() {
        stubEmptyDefaults();
        List<Post> familiar = new java.util.ArrayList<>();
        List<Object[]> tagRows = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String id = "familiar" + i;
            familiar.add(post(id, "author" + i, 5, 5)); // real engagement, unlike the novel post below
            tagRows.add(new Object[] {id, "ai"});
        }
        Post novel = post("novel", "authorNovel", 0, 0); // no engagement, no matching tag at all
        List<Post> pool = new java.util.ArrayList<>(familiar);
        pool.add(novel);

        when(affinityService.affinityMap("viewer")).thenReturn(Map.of("HASHTAG:ai", 1.5));
        when(candidatePoolService.gather(eq("viewer"), any(), any())).thenReturn(pool);
        when(postHashtagRepository.findTagsByPostIds(anyCollection())).thenReturn(tagRows);
        when(postHashtagRepository.findTrending(eq("viewer"), any(), any(Pageable.class))).thenReturn(List.of());
        when(postInteractionRepository.findRecentlyInteractedPostIds(eq("viewer"), anyCollection(), any())).thenReturn(Set.of());

        service().list("viewer", 10, List.of());

        ArgumentCaptor<List<Post>> selected = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(feedService).toDtoList(selected.capture(), eq("viewer"));
        assertThat(selected.getValue()).hasSize(10);
        assertThat(selected.getValue()).extracting(Post::getId).contains("novel");
    }

    @Test
    void diversificationNeverDropsAPostJustToSatisfyTheSameAuthorCap() {
        stubEmptyDefaults();
        // All 5 candidates share one author — more than MAX_CONSECUTIVE_SAME_AUTHOR (2), but there's
        // nothing else to fill the feed with, so the cap must never cause fewer than `size` results.
        List<Post> pool = List.of(
                post("p1", "sameAuthor", 5, 0), post("p2", "sameAuthor", 4, 0), post("p3", "sameAuthor", 3, 0),
                post("p4", "sameAuthor", 2, 0), post("p5", "sameAuthor", 1, 0));
        when(affinityService.affinityMap("viewer")).thenReturn(Map.of());
        when(candidatePoolService.gather(eq("viewer"), any(), any())).thenReturn(pool);
        when(postHashtagRepository.findTagsByPostIds(anyCollection())).thenReturn(List.of());
        when(postHashtagRepository.findTrending(eq("viewer"), any(), any(Pageable.class))).thenReturn(List.of());
        when(postInteractionRepository.findRecentlyInteractedPostIds(eq("viewer"), anyCollection(), any())).thenReturn(Set.of());

        service().list("viewer", 5, List.of());

        ArgumentCaptor<List<Post>> selected = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(feedService).toDtoList(selected.capture(), eq("viewer"));
        assertThat(selected.getValue()).hasSize(5);
    }

    @Test
    void aRecentlyInteractedPostIsDemotedButStillReturnedWhenNeededToFillTheFeed() {
        stubEmptyDefaults();
        Post recentlyLiked = post("liked", "author1", 5, 5);
        Post other = post("other", "author2", 5, 5);
        when(affinityService.affinityMap("viewer")).thenReturn(Map.of());
        when(candidatePoolService.gather(eq("viewer"), any(), any())).thenReturn(List.of(recentlyLiked, other));
        when(postHashtagRepository.findTagsByPostIds(anyCollection())).thenReturn(List.of());
        when(postHashtagRepository.findTrending(eq("viewer"), any(), any(Pageable.class))).thenReturn(List.of());
        when(postInteractionRepository.findRecentlyInteractedPostIds(eq("viewer"), anyCollection(), any()))
                .thenReturn(Set.of("liked"));

        service().list("viewer", 2, List.of());

        ArgumentCaptor<List<Post>> selected = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(feedService).toDtoList(selected.capture(), eq("viewer"));
        // Both make it in (demotion, not exclusion) — but "other" (not demoted) ranks first despite
        // otherwise-identical scores.
        assertThat(selected.getValue()).extracting(Post::getId).containsExactly("other", "liked");
    }

    private PostHashtagRepository.TagCount tagCount(String tag, long count) {
        return new PostHashtagRepository.TagCount() {
            @Override public String getTag() { return tag; }
            @Override public long getPostCount() { return count; }
        };
    }
}
