package com.nukkad.matching.text;

import java.util.Arrays;
import java.util.List;

/** Minimal, dependency-free tokenizer for the TF-IDF engine: lowercase, strip punctuation, drop very short tokens. */
public final class Tokenizer {

    private Tokenizer() {
    }

    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+"))
                .filter(token -> token.length() >= 2)
                .toList();
    }
}
