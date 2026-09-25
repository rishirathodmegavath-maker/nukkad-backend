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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real V111 migration against a real MySQL instance, with hand-inserted rows planted
 *  between V110 and V111 so the floor backfill actually has something to raise. */
@Testcontainers
class PlatformEngagementFloorMigrationTest {

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
                "INSERT INTO users (id, name, email, password_hash) VALUES (?, 'Floor Author', ?, 'x')")) {
            ps.setString(1, id);
            ps.setString(2, id + "@nukkad.test");
            ps.executeUpdate();
        }
        return id;
    }

    private String insertPost(Connection c, String authorId, boolean postedAsPlatform, int engagement) throws Exception {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO posts (id, author_id, content, posted_as_platform, platform_engagement_count) VALUES (?, ?, 'x', ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, authorId);
            ps.setBoolean(3, postedAsPlatform);
            ps.setInt(4, engagement);
            ps.executeUpdate();
        }
        return id;
    }

    private int engagementOf(Connection c, String postId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT platform_engagement_count FROM posts WHERE id = ?")) {
            ps.setString(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getInt(1);
            }
        }
    }

    @Test
    void raisesOnlyEligiblePlatformPostsBelowTheFloorAndIsIdempotent() throws Exception {
        flywayTargeting("110").migrate();

        try (Connection c = connect()) {
            String authorId = insertUser(c);
            String belowFloor = insertPost(c, authorId, true, 3);
            String atFloorAlready = insertPost(c, authorId, true, 15);
            String aboveFloor = insertPost(c, authorId, true, 42);
            String memberBelowFloor = insertPost(c, authorId, false, 3);

            flywayTargeting(null).migrate();

            assertThat(engagementOf(c, belowFloor)).isEqualTo(15);
            assertThat(engagementOf(c, atFloorAlready)).isEqualTo(15);
            assertThat(engagementOf(c, aboveFloor)).as("never lowers a post already above the floor").isEqualTo(42);
            assertThat(engagementOf(c, memberBelowFloor)).as("member posts are never touched").isEqualTo(3);

            // Re-running the exact backfill UPDATE a second time must be a no-op.
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE posts SET platform_engagement_count = 15 WHERE posted_as_platform = TRUE AND platform_engagement_count < 15")) {
                ps.executeUpdate();
            }
            assertThat(engagementOf(c, belowFloor)).isEqualTo(15);
            assertThat(engagementOf(c, aboveFloor)).isEqualTo(42);
        }
    }
}
