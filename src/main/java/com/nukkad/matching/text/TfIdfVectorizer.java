package com.nukkad.matching.text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, dependency-free TF-IDF: no ML library, no training step — smoothed IDF
 * ({@code idf(t) = ln(N / df(t)) + 1}, scikit-learn's default smoothing) fit once over a corpus, then
 * each document is vectorized as {@code tf(t, doc) * idf(t)}. The {@code +1} matters in practice: without
 * it, a term appearing in every document of a tiny corpus (e.g. matching one candidate against one
 * profile) gets {@code idf = ln(1) = 0}, silently zeroing out real overlap — smoothing keeps every term
 * that appears at all with a positive, if small, weight.
 */
public final class TfIdfVectorizer {

    private TfIdfVectorizer() {
    }

    /** One IDF value per term found anywhere in {@code documents}. */
    public static Map<String, Double> fit(List<String> documents) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String doc : documents) {
            Set<String> seenInDoc = Set.copyOf(Tokenizer.tokenize(doc));
            for (String term : seenInDoc) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        int corpusSize = documents.size();
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            idf.put(entry.getKey(), Math.log((double) corpusSize / entry.getValue()) + 1.0);
        }
        return idf;
    }

    /** term -> tf-idf weight for one document, using IDF values already fit over the corpus. */
    public static Map<String, Double> vectorize(String document, Map<String, Double> idf) {
        List<String> tokens = Tokenizer.tokenize(document);
        if (tokens.isEmpty()) return Map.of();

        Map<String, Integer> termFrequency = new HashMap<>();
        for (String token : tokens) {
            termFrequency.merge(token, 1, Integer::sum);
        }

        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            double tf = (double) entry.getValue() / tokens.size();
            // Terms outside the fitted corpus (e.g. a query document with novel words) contribute nothing —
            // there's no document frequency to derive an IDF from, so they're dropped rather than guessed at.
            Double idfValue = idf.get(entry.getKey());
            if (idfValue != null) {
                vector.put(entry.getKey(), tf * idfValue);
            }
        }
        return vector;
    }
}
