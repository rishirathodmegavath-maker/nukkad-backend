package com.nukkad.matching.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TfIdfCosineSimilarityTest {

    @Test
    void strongOverlapScoresHigherThanNoOverlap() {
        // Scenario from the product spec: a user skilled in the idea's exact domain should rank above
        // an unrelated idea. Corpus includes several background documents (as it would in real ranking
        // over many ideas) rather than just the two being compared, so IDF reflects realistic usage.
        String userProfile = "Python AI Computer Vision Deep Learning looking for Startup Co-founder";
        String matchingIdea = "AI-powered crop disease detection using computer vision";
        String unrelatedIdea = "Handmade pottery marketplace for local artisans";
        List<String> background = List.of(
                "Peer-to-peer bicycle rental app for college campuses",
                "Subscription box for regional Indian snacks",
                "Freelance tutoring marketplace for high school students"
        );

        List<String> corpus = new java.util.ArrayList<>(List.of(userProfile, matchingIdea, unrelatedIdea));
        corpus.addAll(background);
        Map<String, Double> idf = TfIdfVectorizer.fit(corpus);

        Map<String, Double> userVector = TfIdfVectorizer.vectorize(userProfile, idf);
        Map<String, Double> matchingVector = TfIdfVectorizer.vectorize(matchingIdea, idf);
        Map<String, Double> unrelatedVector = TfIdfVectorizer.vectorize(unrelatedIdea, idf);

        double matchingScore = CosineSimilarity.compute(userVector, matchingVector);
        double unrelatedScore = CosineSimilarity.compute(userVector, unrelatedVector);

        assertThat(matchingScore).isGreaterThan(unrelatedScore);
        assertThat(matchingScore).isGreaterThan(0.0);
        // Not exactly zero: both documents share a common word ("for"), which a simple tokenizer with no
        // stopword list doesn't filter out. What matters for ranking is that it's negligible next to the real match.
        assertThat(unrelatedScore).isLessThan(matchingScore / 5);
    }

    @Test
    void identicalDocumentsScorePerfectSimilarity() {
        // A third, different document keeps at least one term's document-frequency below the corpus
        // size — if every document contained the exact same terms, every IDF would be ln(1)=0 and the
        // vectors would degenerate to all-zero, which is a real property of TF-IDF, not what this test
        // is checking (that cosine similarity of two truly identical vectors is 1.0).
        Map<String, Double> idf = TfIdfVectorizer.fit(List.of("Python AI Backend", "Python AI Backend", "Marketing Sales"));
        Map<String, Double> a = TfIdfVectorizer.vectorize("Python AI Backend", idf);
        Map<String, Double> b = TfIdfVectorizer.vectorize("Python AI Backend", idf);

        assertThat(CosineSimilarity.compute(a, b)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void emptyDocumentsScoreZero() {
        Map<String, Double> idf = TfIdfVectorizer.fit(List.of("something"));
        assertThat(CosineSimilarity.compute(Map.of(), TfIdfVectorizer.vectorize("something", idf))).isEqualTo(0.0);
    }

    @Test
    void tokenizerLowercasesStripsPunctuationAndDropsShortTokens() {
        List<String> tokens = Tokenizer.tokenize("AI/ML, Computer-Vision! a I of");
        assertThat(tokens).containsExactly("ai", "ml", "computer", "vision", "of");
    }
}
