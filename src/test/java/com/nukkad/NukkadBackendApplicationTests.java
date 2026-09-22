package com.nukkad;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application context against a real MySQL database, running every Flyway
 * migration from scratch and then letting Hibernate validate every entity against the resulting
 * schema (spring.jpa.hibernate.ddl-auto=validate) -- the exact check that took production down
 * when post_votes.value was hand-written as TINYINT (V95) while its mapped entity field is a
 * plain Java `int`, which Hibernate validates against INTEGER (see V98). No prior test in this
 * suite exercised the complete migration chain end-to-end, so this mismatch only surfaced in
 * production. If a future migration's column type stops matching its entity's mapped type, for
 * any table, this test fails the same way the production boot did instead of passing silently.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NukkadBackendApplicationTests {

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

    @Test
    void contextLoadsAndSchemaValidatesAgainstEveryFlywayMigration() {
        // Intentionally empty: SpringBootTest already failed this test during context startup
        // if Flyway migration or Hibernate schema validation threw, exactly as it did in
        // production. Reaching this line is the assertion.
    }
}
