package com.nukkad.grant.discovery;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises GeminiClient's response-JSON-extraction logic (extractText, package-private for this
 * exact purpose) with canned response bodies -- never the real Gemini API, per review instruction.
 * generateContent() itself (the part that actually opens a network connection) is intentionally not
 * called here for the same reason; the "not configured" early-exit path below is the one part of
 * generateContent that's both meaningful to test and reachable with zero network access.
 */
class GeminiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GrantDiscoveryProperties properties = new GrantDiscoveryProperties(
            true, "fake-key-should-never-appear-in-any-message", "gemini-2.5-flash", 10,
            java.util.List.of("Central Government"), java.util.List.of("General"), "0 17 3 * * *", "0 0 4 * * *");
    private final GeminiClient client = new GeminiClient(properties, objectMapper);

    // A trimmed but structurally realistic Gemini generateContent response -- includes sibling
    // fields (finishReason, safetyRatings-shaped extras, usageMetadata) that a real response would
    // have and this client doesn't care about, proving the Map-based parsing tolerates them.
    private static final String REALISTIC_RESPONSE = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "[{\\"grantSchemeName\\":\\"Test Scheme\\"}]" }
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP",
                  "index": 0
                }
              ],
              "usageMetadata": { "promptTokenCount": 42, "candidatesTokenCount": 7 },
              "modelVersion": "gemini-2.5-flash"
            }
            """;

    @Test
    void extractsTextFromRealisticResponse() {
        String text = client.extractText(REALISTIC_RESPONSE);
        assertThat(text).isEqualTo("[{\"grantSchemeName\":\"Test Scheme\"}]");
    }

    @Test
    void concatenatesMultipleParts() {
        String body = """
                {"candidates":[{"content":{"parts":[{"text":"[1,"},{"text":"2,3]"}]}}]}
                """;
        assertThat(client.extractText(body)).isEqualTo("[1,2,3]");
    }

    @Test
    void rejectsEmptyCandidatesArray_withoutLeakingTheKey() {
        String body = """
                {"candidates":[]}
                """;
        assertThatThrownBy(() -> client.extractText(body))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no candidates")
                .hasMessageNotContaining("fake-key-should-never-appear-in-any-message");
    }

    @Test
    void rejectsMissingCandidatesField() {
        assertThatThrownBy(() -> client.extractText("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no candidates");
    }

    @Test
    void rejectsCandidateWithNoContent() {
        String body = """
                {"candidates":[{"finishReason":"SAFETY"}]}
                """;
        assertThatThrownBy(() -> client.extractText(body))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no content");
    }

    @Test
    void rejectsEmptyPartsText() {
        String body = """
                {"candidates":[{"content":{"parts":[]}}]}
                """;
        assertThatThrownBy(() -> client.extractText(body))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty text");
    }

    @Test
    void rejectsMalformedJson_withoutNetworkCall() {
        assertThatThrownBy(() -> client.extractText("not valid json at all"))
                .isInstanceOf(RuntimeException.class); // Jackson's own parse exception, not a network error
    }

    @Test
    void generateContent_failsFastOnMissingApiKey_withoutAttemptingANetworkCall() {
        GrantDiscoveryProperties noKey = new GrantDiscoveryProperties(
                true, "", "gemini-2.5-flash", 10, java.util.List.of("Central Government"), java.util.List.of("General"),
                "0 17 3 * * *", "0 0 4 * * *");
        GeminiClient clientWithNoKey = new GeminiClient(noKey, objectMapper);

        assertThatThrownBy(() -> clientWithNoKey.generateContent("any prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gemini API key is not configured (set GEMINI_API_KEY)");
    }
}
