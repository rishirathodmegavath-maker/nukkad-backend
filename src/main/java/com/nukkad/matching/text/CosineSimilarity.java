package com.nukkad.matching.text;

import java.util.Map;
import java.util.Set;

/** Cosine similarity between two sparse term-weight vectors. Bounded [0,1] for the non-negative TF-IDF weights this is used with. */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    public static double compute(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        Set<String> sharedTerms = a.size() <= b.size() ? a.keySet() : b.keySet();
        double dotProduct = 0.0;
        for (String term : sharedTerms) {
            Double weightA = a.get(term);
            Double weightB = b.get(term);
            if (weightA != null && weightB != null) {
                dotProduct += weightA * weightB;
            }
        }

        double magnitudeA = magnitude(a);
        double magnitudeB = magnitude(b);
        if (magnitudeA == 0.0 || magnitudeB == 0.0) return 0.0;

        return dotProduct / (magnitudeA * magnitudeB);
    }

    private static double magnitude(Map<String, Double> vector) {
        double sumOfSquares = 0.0;
        for (double weight : vector.values()) {
            sumOfSquares += weight * weight;
        }
        return Math.sqrt(sumOfSquares);
    }
}
