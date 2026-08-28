package com.nukkad.matching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Bounded min-heap top-K selection: O(N log K) instead of sorting the full candidate list (O(N log N)).
 * Only ever holds K elements at a time, regardless of how large the input collection is.
 */
public final class TopKSelector {

    private TopKSelector() {
    }

    public static <T> List<T> topK(Collection<T> items, int k, Comparator<T> byScoreAscending) {
        if (k <= 0 || items.isEmpty()) return List.of();

        PriorityQueue<T> minHeap = new PriorityQueue<>(k, byScoreAscending);
        for (T item : items) {
            if (minHeap.size() < k) {
                minHeap.offer(item);
            } else if (byScoreAscending.compare(item, minHeap.peek()) > 0) {
                minHeap.poll();
                minHeap.offer(item);
            }
        }

        List<T> result = new ArrayList<>(minHeap);
        result.sort(byScoreAscending.reversed());
        return result;
    }
}
