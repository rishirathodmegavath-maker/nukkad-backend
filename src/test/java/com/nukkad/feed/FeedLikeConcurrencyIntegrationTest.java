package com.nukkad.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nukkad.feed.repository.PostLikeRepository;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the concurrent-duplicate-like race directly: N threads all send the like-toggle
 * request for the same not-yet-liked post at once. MySQL/InnoDB can resolve that race either as a
 * clean unique-constraint violation or, with three or more concurrent inserts of the same key, a
 * genuine deadlock between the waiters — the fix under test is that the controller treats both
 * outcomes as "someone else already liked it" rather than letting either surface as a 500.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeedLikeConcurrencyIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
            .withDatabaseName("nukkad_test")
            .withUsername("nukkad_test")
            .withPassword("nukkad_test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("nukkad.jwt.secret", () -> "integration-test-secret-key-long-enough-for-hmac-sha-512-signing");
        registry.add("nukkad.jwt.access-expiration-seconds", () -> "900");
        registry.add("nukkad.jwt.refresh-expiration-seconds", () -> "604800");
        registry.add("nukkad.cors.allowed-origins", () -> "http://localhost:5174");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PostLikeRepository postLikeRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    private String registerVerifyAndLogin(String email) throws Exception {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);

        String registerPayload = """
                {"name":"Concurrency Test","email":"%s","password":"Password123!"}
                """.formatted(email);
        rest.postForEntity(url("/auth/register"), new HttpEntity<>(registerPayload, json), String.class);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        String loginPayload = """
                {"email":"%s","password":"Password123!"}
                """.formatted(email);
        var loginResponse = rest.postForEntity(url("/auth/login"), new HttpEntity<>(loginPayload, json), String.class);
        return objectMapper.readTree(loginResponse.getBody()).get("data").get("accessToken").asText();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void concurrentLikeRequestsForTheSameUserAndPostResultInExactlyOneLike() throws Exception {
        String email = "concurrency-" + System.nanoTime() + "@nukkad.app";
        String token = registerVerifyAndLogin(email);

        var createPost = rest.exchange(url("/feed"), HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"concurrency test post\",\"type\":\"text\"}", authHeaders(token)), String.class);
        String postId = objectMapper.readTree(createPost.getBody()).get("data").get("id").asText();

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger non200Count = new AtomicInteger(0);
        List<HttpStatus> statuses = new java.util.concurrent.CopyOnWriteArrayList<>();

        try {
            var futures = IntStream.range(0, threadCount).mapToObj(i -> pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                var response = rest.exchange(url("/feed/" + postId + "/like"), HttpMethod.POST,
                        new HttpEntity<>(authHeaders(token)), String.class);
                statuses.add((HttpStatus) response.getStatusCode());
                if (response.getStatusCode().value() != 200) {
                    non200Count.incrementAndGet();
                }
            })).toList();

            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            for (var f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(non200Count.get())
                .as("all concurrent like requests should resolve cleanly (statuses=%s), never a 500", statuses)
                .isZero();

        long likeRows = postLikeRepository.findAll().stream()
                .filter(l -> l.getPostId().equals(postId))
                .count();
        assertThat(likeRows).as("exactly one like row must exist no matter how many concurrent requests raced").isEqualTo(1);

        var finalState = rest.exchange(url("/feed/" + postId), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), String.class);
        JsonNode data = objectMapper.readTree(finalState.getBody()).get("data");
        assertThat(data.get("likesCount").asInt()).isEqualTo(1);
        assertThat(data.get("isLiked").asBoolean()).isTrue();
    }
}
