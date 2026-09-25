package com.nukkad.feed;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real V112 migration against a real MySQL instance: hand-inserted rows planted between
 *  V111 and V112, still holding the old six-name values V110 would have assigned, prove the enum
 *  swap remaps every row — platform posts to a deterministic four-way split, member posts to the new
 *  meaningless default — rather than leaving a now-invalid string Hibernate can't deserialize. */
@Testcontainers
class PublisherIdentityReplacementMigrationTest {

    private static final List<String> IDENTITIES = List.of("ARJUN_MEHTA", "KARAN_SHAH", "NEEL_KAPOOR", "VIKRAM_RAO");

    @Container
    final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.39")
            .withDatabaseName("nukkad_test")
            .withUsername("nukkad_test")
            .withPassword("nukkad_test");

    private Flyway flywayTargeting(String target) {
        FluentConfiguration config = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private String insertUser(Connection c) throws Exception {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (id, name, email, password_hash) VALUES (?, 'Identity Author', ?, 'x')")) {
            ps.setString(1, id);
            ps.setString(2, id + "@nukkad.test");
            ps.executeUpdate();
        }
        return id;
    }

    /** {@code oldIdentity} mirrors whatever V110 would have already assigned — 'BUILDADDA' etc — but
     *  the assertions below never depend on it: V112's mapping is a pure function of (created_at, id). */
    private String insertPost(Connection c, String authorId, boolean postedAsPlatform, Instant createdAt, String oldIdentity) throws Exception {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO posts (id, author_id, content, posted_as_platform, publisher_identity, created_at) VALUES (?, ?, 'x', ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, authorId);
            ps.setBoolean(3, postedAsPlatform);
            ps.setString(4, oldIdentity);
            ps.setTimestamp(5, Timestamp.from(createdAt));
            ps.executeUpdate();
        }
        return id;
    }

    private Map<String, String> publisherIdentitiesById(Connection c) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id, publisher_identity FROM posts ORDER BY created_at, id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("id"), rs.getString("publisher_identity"));
            }
        }
        return result;
    }

    @Test
    void remapsEveryPostToTheFourNewIdentitiesDeterministicallyAndIsIdempotent() throws Exception {
        flywayTargeting("111").migrate();

        Instant base = Instant.parse("2026-02-01T00:00:00Z");
        try (Connection c = connect()) {
            String authorId = insertUser(c);

            List<String> platform = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                platform.add(insertPost(c, authorId, true, base.plusSeconds(60L * i), "BUILDADDA"));
            }
            String memberA = insertPost(c, authorId, false, base.plusSeconds(3600), "BUILDADDA");
            String memberB = insertPost(c, authorId, false, base.plusSeconds(3660), "BUILDADDA");

            flywayTargeting(null).migrate();

            Map<String, String> byId = publisherIdentitiesById(c);

            for (int i = 0; i < platform.size(); i++) {
                assertThat(byId.get(platform.get(i)))
                        .as("platform post #%d should round-robin to identity index %d", i, i % 4)
                        .isEqualTo(IDENTITIES.get(i % 4));
            }
            for (String identity : IDENTITIES) {
                long count = byId.values().stream().filter(identity::equals).count();
                assertThat(count).as("identity %s should be used at least twice across 8 platform posts", identity).isGreaterThanOrEqualTo(2);
            }

            assertThat(byId.get(memberA)).as("member posts get the new meaningless default, never an invalid old value")
                    .isEqualTo("ARJUN_MEHTA");
            assertThat(byId.get(memberB)).isEqualTo("ARJUN_MEHTA");

            // Re-running the exact remap a second time must be a no-op.
            try (PreparedStatement ps = c.prepareStatement("UPDATE posts SET publisher_identity = 'ARJUN_MEHTA' WHERE posted_as_platform = FALSE")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE posts p
                    JOIN (
                        SELECT id,
                               ELT(
                                   (ROW_NUMBER() OVER (ORDER BY created_at, id) - 1) % 4 + 1,
                                   'ARJUN_MEHTA', 'KARAN_SHAH', 'NEEL_KAPOOR', 'VIKRAM_RAO'
                               ) AS assigned_identity
                        FROM posts
                        WHERE posted_as_platform = TRUE
                    ) ranked ON ranked.id = p.id
                    SET p.publisher_identity = ranked.assigned_identity
                    """)) {
                ps.executeUpdate();
            }
            assertThat(publisherIdentitiesById(c)).as("re-running the remap must reshuffle nothing").isEqualTo(byId);
        }
    }
}
