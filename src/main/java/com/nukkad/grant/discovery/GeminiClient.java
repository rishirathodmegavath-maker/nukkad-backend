package com.nukkad.grant.discovery;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin client for Gemini's generateContent REST endpoint with Google Search grounding enabled, so
 * the model can actually look up current schemes rather than answer from training data alone.
 * Returns only the model's raw text -- parsing/validating the JSON array inside that text is
 * GrantDiscoveryService's job, not this client's.
 *
 * Deliberately hand-builds the request JSON (rather than serialising a request object graph) and
 * parses the response into plain Map/List (rather than a typed record matching Gemini's full
 * response shape): both sidestep needing to know this project's Jackson-3 fork's exact node/
 * annotation API, and a raw Map tolerates response fields this client doesn't care about without
 * needing to keep a full schema in sync with Google's API.
 */
@Component
public class GeminiClient {

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final GrantDiscoveryProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public GeminiClient(GrantDiscoveryProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String generateContent(String prompt) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured (set GEMINI_API_KEY)");
        }

        String escapedPrompt = objectMapper.writeValueAsString(prompt);
        String requestBody = "{"
                + "\"contents\":[{\"parts\":[{\"text\":" + escapedPrompt + "}]}],"
                + "\"tools\":[{\"google_search\":{}}],"
                + "\"generationConfig\":{\"temperature\":0.1}"
                + "}";

        URI uri = URI.create(ENDPOINT.formatted(properties.model(), properties.apiKey()));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Gemini API call failed: " + e.getMessage(), e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Gemini API returned HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }
        return extractText(response.body());
    }

    /** Package-private (not private) specifically so GeminiClientTest can exercise this parsing
     *  logic directly with canned response bodies, without making a real network call. Note every
     *  message this method can throw is built only from {@code responseBody}/{@code truncate(...)}
     *  -- never from {@code properties.apiKey()} -- so a parsing failure can never leak the key. */
    @SuppressWarnings("unchecked")
    String extractText(String responseBody) {
        Map<String, Object> root = objectMapper.readValue(responseBody, Map.class);
        Object candidatesObj = root.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini API returned no candidates: " + truncate(responseBody));
        }

        Map<String, Object> firstCandidate = (Map<String, Object>) candidates.get(0);
        Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
        if (content == null) {
            throw new IllegalStateException("Gemini API candidate had no content: " + truncate(responseBody));
        }

        StringBuilder text = new StringBuilder();
        Object partsObj = content.get("parts");
        if (partsObj instanceof List<?> parts) {
            for (Object part : parts) {
                Object t = ((Map<String, Object>) part).get("text");
                if (t != null) text.append(t);
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("Gemini API returned empty text: " + truncate(responseBody));
        }
        return text.toString();
    }

    private String truncate(String s) {
        return s.length() > 800 ? s.substring(0, 800) + "…" : s;
    }
}
