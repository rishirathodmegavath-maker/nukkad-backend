package com.nukkad.matching;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopKSelectorTest {

    private record Scored(String name, double score) {
    }

    @Test
    void returnsTopKRegardlessOfInputOrder() {
        List<Scored> candidates = List.of(
                new Scored("low", 0.1),
                new Scored("highest", 0.95),
                new Scored("mid", 0.5),
                new Scored("second", 0.8),
                new Scored("lowest", 0.01)
        );

        List<Scored> top3 = TopKSelector.topK(candidates, 3, Comparator.comparingDouble(Scored::score));

        assertThat(top3).extracting(Scored::name).containsExactly("highest", "second", "mid");
    }

    @Test
    void kLargerThanInputReturnsEverythingSorted() {
        List<Scored> candidates = List.of(new Scored("a", 0.2), new Scored("b", 0.9));

        List<Scored> result = TopKSelector.topK(candidates, 10, Comparator.comparingDouble(Scored::score));

        assertThat(result).extracting(Scored::name).containsExactly("b", "a");
    }

    @Test
    void zeroKReturnsEmpty() {
        List<Scored> candidates = List.of(new Scored("a", 0.2));
        assertThat(TopKSelector.topK(candidates, 0, Comparator.comparingDouble(Scored::score))).isEmpty();
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertThat(TopKSelector.<Scored>topK(List.of(), 5, Comparator.comparingDouble(Scored::score))).isEmpty();
    }
}
