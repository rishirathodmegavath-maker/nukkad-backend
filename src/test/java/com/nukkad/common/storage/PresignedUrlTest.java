package com.nukkad.common.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Exercises the REAL AWS SDK presigner that {@link S3Config} builds — no mocks, no network. Presigning is a
 * purely local SigV4 computation, so what the URL encodes (its expiry, the object it is scoped to, the method
 * and headers it signs, whether any secret leaks into it) can be asserted directly here.
 *
 * <p>What this cannot show is MinIO's server-side reaction — that a wrong signature or an expired URL is
 * answered with 403. That is standard SigV4 behaviour of the storage server and needs a running MinIO to
 * observe; see the manual verification steps in the security report.
 */
class PresignedUrlTest {

    private static final String ACCESS_KEY_ID = "TESTKEYID0123456789";
    private static final String SECRET_KEY = "test-secret-key-must-never-appear-in-a-url-0123456789";
    private static final String CONVERSATION_KEY = "messages/conv1/3f2b8c1e-1111-4222-8333-444455556666.png";
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private static String previousKeyId;
    private static String previousSecret;

    @BeforeAll
    static void useStaticTestCredentials() {
        previousKeyId = System.getProperty("aws.accessKeyId");
        previousSecret = System.getProperty("aws.secretAccessKey");
        System.setProperty("aws.accessKeyId", ACCESS_KEY_ID);
        System.setProperty("aws.secretAccessKey", SECRET_KEY);
    }

    @AfterAll
    static void restoreCredentials() {
        restore("aws.accessKeyId", previousKeyId);
        restore("aws.secretAccessKey", previousSecret);
    }

    private static void restore(String property, String previous) {
        if (previous == null) System.clearProperty(property);
        else System.setProperty(property, previous);
    }

    private FileStorageService storage() {
        // The same shape as production: an internal Docker-network endpoint for the client, a public host for URLs.
        StorageProperties properties = new StorageProperties("nukkad-uploads", "us-east-1", "http://minio:9000", true,
                "https://media.example.com/nukkad-uploads");
        S3Presigner presigner = new S3Config().s3Presigner(properties);
        return new FileStorageService(mock(S3Client.class), presigner, properties);
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> params = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(parts[0], URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return params;
    }

    @Test
    void theUrlPointsAtThePublicHostAndIsScopedToExactlyTheRequestedBucketAndKey() {
        URI url = URI.create(storage().presignGet(CONVERSATION_KEY, Duration.ofHours(1)));

        // The browser-reachable public host, not the internal Docker address the client talks to.
        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("media.example.com");
        assertThat(url.getPath()).isEqualTo("/nukkad-uploads/" + CONVERSATION_KEY);
    }

    @Test
    void theUrlExpiresAfterExactlyTheRequestedDuration() {
        Instant before = Instant.now().minusSeconds(2);
        Map<String, String> params = query(URI.create(storage().presignGet(CONVERSATION_KEY, Duration.ofHours(1))));
        Instant after = Instant.now().plusSeconds(2);

        assertThat(params.get("X-Amz-Expires")).isEqualTo("3600");
        Instant signedAt = LocalDateTime.parse(params.get("X-Amz-Date"), AMZ_DATE).toInstant(ZoneOffset.UTC);
        assertThat(signedAt).isBetween(before.truncatedTo(java.time.temporal.ChronoUnit.SECONDS), after);
        // "Valid until" is the signing time plus that duration: an hour from now, not six, not never.
        Instant validUntil = signedAt.plusSeconds(Long.parseLong(params.get("X-Amz-Expires")));
        assertThat(Duration.between(Instant.now(), validUntil)).isBetween(Duration.ofMinutes(59), Duration.ofMinutes(61));
    }

    @Test
    void aDifferentDurationIsReflectedInTheUrl() {
        assertThat(query(URI.create(storage().presignGet(CONVERSATION_KEY, Duration.ofMinutes(5)))).get("X-Amz-Expires")).isEqualTo("300");
    }

    @Test
    void theUrlSignsAGetRequestOnTheHostHeaderOnlyUsingSigV4() {
        Map<String, String> params = query(URI.create(storage().presignGet(CONVERSATION_KEY, Duration.ofHours(1))));

        assertThat(params.get("X-Amz-Algorithm")).isEqualTo("AWS4-HMAC-SHA256");
        // Only "host" is a signed header, and a GetObject presign signs the GET method into the canonical
        // request — so the same URL can't be replayed as a PUT or DELETE.
        assertThat(params.get("X-Amz-SignedHeaders")).isEqualTo("host");
        assertThat(params.get("X-Amz-Signature")).matches("[0-9a-f]{64}");
        assertThat(params.get("X-Amz-Credential")).endsWith("/us-east-1/s3/aws4_request");
    }

    @Test
    void theUrlCarriesTheAccessKeyIdButNeverTheSecretOrASessionToken() {
        String url = storage().presignGet(CONVERSATION_KEY, Duration.ofHours(1));

        assertThat(url).doesNotContain(SECRET_KEY);
        assertThat(url).doesNotContain("secret");
        assertThat(query(URI.create(url))).doesNotContainKey("X-Amz-Security-Token");
        // The access key ID is a public identifier in every SigV4 URL (it names which key signed), not a secret.
        assertThat(query(URI.create(url)).get("X-Amz-Credential")).startsWith(ACCESS_KEY_ID + "/");
    }

    @Test
    void eachObjectGetsItsOwnSignatureSoOneUrlCannotBeReusedForAnotherObject() {
        FileStorageService storage = storage();
        String otherKey = "messages/conv1/aaaaaaaa-1111-4222-8333-444455556666.png";

        URI first = URI.create(storage.presignGet(CONVERSATION_KEY, Duration.ofHours(1)));
        URI second = URI.create(storage.presignGet(otherKey, Duration.ofHours(1)));

        assertThat(first.getPath()).isNotEqualTo(second.getPath());
        assertThat(query(first).get("X-Amz-Signature")).isNotEqualTo(query(second).get("X-Amz-Signature"));
    }

    @Test
    void aTraversalStyleKeyIsNotSilentlyNormalizedIntoAnotherObjectsPath() {
        // The presigner is a signer, not a validator — which is exactly why ConversationService only ever passes
        // it a key it has already proven is one of its own chat-attachment keys (FileStorageService.
        // isConversationAttachmentKey), and never a raw client-supplied string.
        assertThat(FileStorageService.isConversationAttachmentKey("messages/conv1/../../feed/x.png", "conv1")).isFalse();
    }
}
