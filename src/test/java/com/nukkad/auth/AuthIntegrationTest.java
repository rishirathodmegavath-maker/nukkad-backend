package com.nukkad.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    @Test
    void registerLoginRefreshAndAccessProtectedEndpoint() throws Exception {
        String email = "integration-" + System.nanoTime() + "@nukkad.app";

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        // Unauthenticated access is rejected with the standard error envelope.
        var unauthenticated = rest.getForEntity(url("/users/me"), String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode unauthBody = objectMapper.readTree(unauthenticated.getBody());
        assertThat(unauthBody.get("success").asBoolean()).isFalse();
        assertThat(unauthBody.get("errorCode").asText()).isEqualTo("UNAUTHORIZED");

        // Register — no tokens are issued anymore; the account starts unverified.
        String registerPayload = """
                {"name":"Integration Test","email":"%s","password":"Password123!"}
                """.formatted(email);
        var registerResponse = rest.postForEntity(url("/auth/register"), new HttpEntity<>(registerPayload, jsonHeaders), String.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode registerBody = objectMapper.readTree(registerResponse.getBody()).get("data");
        assertThat(registerBody.get("email").asText()).isEqualTo(email);

        // Logging in before verification is rejected — no session is issued.
        String loginPayload = """
                {"email":"%s","password":"Password123!"}
                """.formatted(email);
        var loginBeforeVerify = rest.postForEntity(url("/auth/login"), new HttpEntity<>(loginPayload, jsonHeaders), String.class);
        assertThat(loginBeforeVerify.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(objectMapper.readTree(loginBeforeVerify.getBody()).get("errorCode").asText()).isEqualTo("EMAIL_NOT_VERIFIED");

        // Simulate clicking the emailed verification link (the raw token itself is only ever
        // emailed, never stored — flipping the flag directly is the test-only equivalent).
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        // Login now succeeds and issues a session.
        var loginResponse = rest.postForEntity(url("/auth/login"), new HttpEntity<>(loginPayload, jsonHeaders), String.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode loginBody = objectMapper.readTree(loginResponse.getBody()).get("data");
        String firstAccessToken = loginBody.get("accessToken").asText();
        String firstRefreshToken = loginBody.get("refreshToken").asText();

        // Access a protected endpoint with the freshly issued access token.
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(firstAccessToken);
        var meResponse = rest.exchange(url("/users/me"), HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);
        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(meResponse.getBody()).get("data").get("email").asText()).isEqualTo(email);

        // Wrong password is rejected.
        String wrongPasswordPayload = """
                {"email":"%s","password":"wrong-password"}
                """.formatted(email);
        var badLogin = rest.postForEntity(url("/auth/login"), new HttpEntity<>(wrongPasswordPayload, jsonHeaders), String.class);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Refresh rotates the token.
        String refreshPayload = "{\"refreshToken\":\"" + firstRefreshToken + "\"}";
        var refreshResponse = rest.postForEntity(url("/auth/refresh"), new HttpEntity<>(refreshPayload, jsonHeaders), String.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode refreshBody = objectMapper.readTree(refreshResponse.getBody()).get("data");
        String rotatedRefreshToken = refreshBody.get("refreshToken").asText();
        assertThat(rotatedRefreshToken).isNotEqualTo(firstRefreshToken);

        // New access token from the rotation also works against a protected endpoint.
        HttpHeaders rotatedAuthHeaders = new HttpHeaders();
        rotatedAuthHeaders.setBearerAuth(refreshBody.get("accessToken").asText());
        var meAfterRefresh = rest.exchange(url("/users/me"), HttpMethod.GET, new HttpEntity<>(rotatedAuthHeaders), String.class);
        assertThat(meAfterRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Reusing the now-rotated-away refresh token is a reuse-detection signal -> rejected.
        var reuseResponse = rest.postForEntity(url("/auth/refresh"), new HttpEntity<>(refreshPayload, jsonHeaders), String.class);
        assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void duplicateEmailRegistrationIsRejectedWithConflict() {
        String email = "dup-" + System.nanoTime() + "@nukkad.app";
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        String payload = """
                {"name":"Dup User","email":"%s","password":"Password123!"}
                """.formatted(email);

        var first = rest.postForEntity(url("/auth/register"), new HttpEntity<>(payload, jsonHeaders), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var second = rest.postForEntity(url("/auth/register"), new HttpEntity<>(payload, jsonHeaders), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
