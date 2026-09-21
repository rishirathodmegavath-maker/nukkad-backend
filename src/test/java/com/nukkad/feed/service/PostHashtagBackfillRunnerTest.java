package com.nukkad.feed.service;

import com.nukkad.feed.entity.Post;
import com.nukkad.feed.entity.PostHashtag;
import com.nukkad.feed.repository.PostHashtagRepository;
import com.nukkad.feed.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostHashtagBackfillRunnerTest {

    @Mock private PostRepository postRepository;
    @Mock private PostHashtagRepository postHashtagRepository;

    private PostHashtagBackfillRunner runner() {
        return new PostHashtagBackfillRunner(postRepository, postHashtagRepository);
    }

    private Post post(String id, String content) {
        return Post.builder().id(id).authorId("a").content(content).build();
    }

    @Test
    void recordsTheTagsOfPostsThatPredateHashtags() {
        when(postHashtagRepository.existsBy()).thenReturn(false);
        when(postRepository.findByContentContaining(eq("#"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post("p1", "Hello #AI #startup"), post("p2", "issue #12 only"), post("p3", "#india"))));

        int recorded = runner().backfill();

        assertThat(recorded).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<PostHashtag>> rows = ArgumentCaptor.forClass(Iterable.class);
        verify(postHashtagRepository, org.mockito.Mockito.times(2)).saveAll(rows.capture());
        List<String> saved = new ArrayList<>();
        rows.getAllValues().forEach(batch -> batch.forEach(r -> saved.add(r.getPostId() + ":" + r.getTag())));
        assertThat(saved).containsExactly("p1:ai", "p1:startup", "p3:india");
    }

    @Test
    void doesNothingOnceTheTableHasRows() {
        when(postHashtagRepository.existsBy()).thenReturn(true);

        assertThat(runner().backfill()).isZero();

        verify(postRepository, never()).findByContentContaining(any(), any());
        verify(postHashtagRepository, never()).saveAll(any());
    }

    @Test
    void neverStopsTheServerFromStartingWhateverGoesWrong() {
        when(postHashtagRepository.existsBy()).thenThrow(new IllegalStateException("database is down"));

        assertThatCode(() -> runner().run(null)).doesNotThrowAnyException();
    }
}
