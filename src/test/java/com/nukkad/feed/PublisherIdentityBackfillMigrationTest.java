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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real V110 migration SQL against a real MySQL instance, with hand-inserted
 * historical rows planted between V109 and V110 — the ordinary @SpringBootTest/Testcontainers
 * pattern used elsewhere (see FeedLikeConcurrencyIntegrationTest) can't set this up, since its
 * Flyway run happens once at context startup, before a test method ever gets to insert the "already
 * existing" platform posts V110 is meant to backfill.
 */
@Testcontainers
class PublisherIdentityBackfillMigrationTest {

    private static final List<String> IDENTITIES = List.of(
            "BUILDADDA", "BUILDADDA_INSIGHTS", "BUILDADDA_GRANTS",
            "BUILDADDA_COMMUNITY", "BUILDADDA_STARTUP_DESK", "BUILDADDA_EDITORIAL");

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
                "INSERT INTO users (id, name, email, password_hash) VALUES (?, 'Backfill Author', ?, 'x')")) {
            ps.setString(1, id);
            ps.setString(2, id + "@nukkad.test");
            ps.executeUpdate();
        }
        return id;
    }

    private String insertPost(Connection c, String authorId, boolean postedAsPlatform, Instant createdAt, int likesCount) throws Exception {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO posts (id, author_id, content, posted_as_platform, likes_count, created_at) VALUES (?, ?, 'historical post', ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, authorId);
            ps.setBoolean(3, postedAsPlatform);
            ps.setInt(4, likesCount);
            ps.setTimestamp(5, Timestamp.from(createdAt));
            ps.executeUpdate();
        }
        return id;
    }

    private Map<String, String> publisherIdentitiesById(Connection c) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, publisher_identity FROM posts ORDER BY created_at, id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("id"), rs.getString("publisher_identity"));
            }
        }
        return result;
    }

    @Test
    void backfillsHistoricalPlatformPostsDeterministicallyWithoutTouchingMemberPostsOrOtherColumns() throws Exception {
        // Migrate only up to the point where the columns exist but the backfill has not run yet.
        flywayTargeting("109").migrate();

        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        try (Connection c = connect()) {
            String authorId = insertUser(c);

            // 12 platform posts at distinct, one-minute-apart timestamps: no ties, so ordering is
            // purely by created_at. Sequence index 0..11 should round-robin twice through all six
            // identities (2 each).
            List<String> sequential = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                sequential.add(insertPost(c, authorId, true, base.plusSeconds(60L * i), 7));
            }

            // Two more platform posts sharing the exact same created_at as each other (and landing
            // after all 12 above) — this is the only way the created_at, id tiebreak actually gets
            // exercised, since MySQL TIMESTAMP has no sub-second precision here.
            Instant tieInstant = base.plusSeconds(60L * 12);
            String tieA = insertPost(c, authorId, true, tieInstant, 7);
            String tieB = insertPost(c, authorId, true, tieInstant, 7);
            List<String> tied = new ArrayList<>(List.of(tieA, tieB));
            tied.sort(String::compareTo); // matches MySQL's ORDER BY id tiebreak for these plain UUID strings

            // 3 ordinary member posts — must never be touched by the backfill.
            String memberPostA = insertPost(c, authorId, false, base.plusSeconds(3600), 3);
            String memberPostB = insertPost(c, authorId, false, base.plusSeconds(3660), 3);
            String memberPostC = insertPost(c, authorId, false, base.plusSeconds(3720), 3);

            // Apply V110.
            flywayTargeting(null).migrate();

            Map<String, String> byId = publisherIdentitiesById(c);

            for (int i = 0; i < sequential.size(); i++) {
                assertThat(byId.get(sequential.get(i)))
                        .as("platform post #%d (0-based, ordered by created_at) should round-robin to identity index %d", i, i % 6)
                        .isEqualTo(IDENTITIES.get(i % 6));
            }
            // The tied pair continues the same round-robin sequence (indices 12 and 13), ordered by id.
            assertThat(byId.get(tied.get(0))).isEqualTo(IDENTITIES.get(12 % 6));
            assertThat(byId.get(tied.get(1))).isEqualTo(IDENTITIES.get(13 % 6));

            // Every identity actually used at least twice: a genuinely balanced spread, not a
            // formula that happens to degenerate onto one value.
            for (String identity : IDENTITIES) {
                long count = byId.values().stream().filter(identity::equals).count();
                assertThat(count).as("identity %s should be used at least twice across 14 platform posts", identity).isGreaterThanOrEqualTo(2);
            }

            // Member posts are completely untouched: still the meaningless V109 default.
            assertThat(byId.get(memberPostA)).isEqualTo("BUILDADDA");
            assertThat(byId.get(memberPostB)).isEqualTo("BUILDADDA");
            assertThat(byId.get(memberPostC)).isEqualTo("BUILDADDA");

            // author_id and likes_count are bit-for-bit unchanged for every row — the migration
            // only ever writes publisher_identity.
            try (PreparedStatement ps = c.prepareStatement("SELECT author_id, likes_count FROM posts WHERE id = ?")) {
                for (String postId : List.of(sequential.get(0), tieA, memberPostA)) {
                    ps.setString(1, postId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getString("author_id")).isEqualTo(authorId);
                        assertThat(rs.getInt("likes_count")).isIn(7, 3);
                    }
                }
            }

            // Re-running the exact backfill UPDATE a second time (simulating a repeat/replay of
            // V110) must be a no-op: the formula depends only on immutable columns, never on the
            // row's current publisher_identity.
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE posts p
                    JOIN (
                        SELECT id,
                               ELT(
                                   (ROW_NUMBER() OVER (ORDER BY created_at, id) - 1) % 6 + 1,
                                   'BUILDADDA', 'BUILDADDA_INSIGHTS', 'BUILDADDA_GRANTS',
                                   'BUILDADDA_COMMUNITY', 'BUILDADDA_STARTUP_DESK', 'BUILDADDA_EDITORIAL'
                               ) AS assigned_identity
                        FROM posts
                        WHERE posted_as_platform = TRUE
                    ) ranked ON ranked.id = p.id
                    SET p.publisher_identity = ranked.assigned_identity
                    """)) {
                ps.executeUpdate();
            }
            assertThat(publisherIdentitiesById(c)).as("re-running the backfill must reshuffle nothing").isEqualTo(byId);
        }
    }
}
