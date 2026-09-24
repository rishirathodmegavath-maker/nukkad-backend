package com.nukkad.feed.recommendation;

import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.PostInteraction;
import com.nukkad.feed.entity.UserTopicAffinity;
import com.nukkad.feed.repository.UserTopicAffinityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTopicAffinityServiceTest {

    @Mock private UserTopicAffinityRepository repository;

    private UserTopicAffinityService service(Instant now) {
        return new UserTopicAffinityService(repository, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Post post(String content, Post.Type type) {
        return Post.builder().id("p1").authorId("author").content(content).type(type).build();
    }

    @Test
    void likingAPostBumpsAffinityForEveryHashtagAndThePostType() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(repository.findByUserIdAndTopicKindAndTopicKey(eq("u1"), any(), any())).thenReturn(Optional.empty());

        service(now).recordSignal("u1", post("Loving #AI and #startups today", Post.Type.text), PostInteraction.Type.LIKE);

        ArgumentCaptor<UserTopicAffinity> saved = ArgumentCaptor.forClass(UserTopicAffinity.class);
        verify(repository, org.mockito.Mockito.times(3)).save(saved.capture());
        List<UserTopicAffinity> rows = saved.getAllValues();
        assertThat(rows).extracting(UserTopicAffinity::getTopicKind, UserTopicAffinity::getTopicKey, UserTopicAffinity::getScore)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(UserTopicAffinity.TopicKind.HASHTAG, "ai", FeedRankingWeights.LIKE_WEIGHT),
                        org.assertj.core.groups.Tuple.tuple(UserTopicAffinity.TopicKind.HASHTAG, "startups", FeedRankingWeights.LIKE_WEIGHT),
                        org.assertj.core.groups.Tuple.tuple(UserTopicAffinity.TopicKind.POST_TYPE, "text", FeedRankingWeights.LIKE_WEIGHT));
    }

    @Test
    void repeatedLikesOnTheSameTopicCompoundInsteadOfJustReflectingTheMostRecentOne() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        UserTopicAffinity existing = UserTopicAffinity.builder()
                .userId("u1").topicKind(UserTopicAffinity.TopicKind.HASHTAG).topicKey("ai")
                .score(FeedRankingWeights.LIKE_WEIGHT).updatedAt(now).build();
        when(repository.findByUserIdAndTopicKindAndTopicKey("u1", UserTopicAffinity.TopicKind.HASHTAG, "ai"))
                .thenReturn(Optional.of(existing));
        when(repository.findByUserIdAndTopicKindAndTopicKey("u1", UserTopicAffinity.TopicKind.POST_TYPE, "text"))
                .thenReturn(Optional.empty());

        service(now).recordSignal("u1", post("More #AI news", Post.Type.text), PostInteraction.Type.LIKE);

        assertThat(existing.getScore()).isEqualTo(FeedRankingWeights.LIKE_WEIGHT * 2);
    }

    @Test
    void anUnlikeExactlyReversesTheLikeItUndoes() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        UserTopicAffinity existing = UserTopicAffinity.builder()
                .userId("u1").topicKind(UserTopicAffinity.TopicKind.POST_TYPE).topicKey("text")
                .score(FeedRankingWeights.LIKE_WEIGHT).updatedAt(now).build();
        when(repository.findByUserIdAndTopicKindAndTopicKey(eq("u1"), any(), any())).thenReturn(Optional.of(existing));

        service(now).recordSignal("u1", post("no tags here", Post.Type.text), PostInteraction.Type.UNLIKE);

        assertThat(existing.getScore()).isEqualTo(0.0);
    }

    @Test
    void hidingAPostAppliesAStrongNegativeAndUnhidingReversesIt() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        UserTopicAffinity existing = UserTopicAffinity.builder()
                .userId("u1").topicKind(UserTopicAffinity.TopicKind.POST_TYPE).topicKey("announcement")
                .score(0.5).updatedAt(now).build();
        when(repository.findByUserIdAndTopicKindAndTopicKey(eq("u1"), any(), any())).thenReturn(Optional.of(existing));

        service(now).recordSignal("u1", post("no tags", Post.Type.announcement), PostInteraction.Type.HIDE);
        assertThat(existing.getScore()).isEqualTo(0.5 + FeedRankingWeights.HIDE_WEIGHT);

        service(now).recordSignal("u1", post("no tags", Post.Type.announcement), PostInteraction.Type.UNHIDE);
        assertThat(existing.getScore()).isEqualTo(0.5);
    }

    @Test
    void affinityDecaysWithElapsedDaysWhenReadLaterButNotBeforeAnyTimeHasPassed() {
        Instant recordedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant readNow = recordedAt.plus(java.time.Duration.ofDays(30));
        UserTopicAffinity row = UserTopicAffinity.builder()
                .userId("u1").topicKind(UserTopicAffinity.TopicKind.HASHTAG).topicKey("ai")
                .score(1.0).updatedAt(recordedAt).build();
        when(repository.findByUserId("u1")).thenReturn(List.of(row));

        var map = service(readNow).affinityMap("u1");

        double expected = Math.pow(FeedRankingWeights.AFFINITY_DECAY_FACTOR_PER_DAY, 30);
        assertThat(map).containsKey("HASHTAG:ai");
        assertThat(map.get("HASHTAG:ai")).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(map.get("HASHTAG:ai")).isLessThan(1.0);
    }

    @Test
    void aColdStartUserWithNoAffinityRowsGetsAnEmptyMapNotAnError() {
        when(repository.findByUserId("newUser")).thenReturn(List.of());

        assertThat(service(Instant.now()).affinityMap("newUser")).isEmpty();
    }
}
