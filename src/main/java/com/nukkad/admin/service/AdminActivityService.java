package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminActivityDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Platform-wide activity timeline for the admin portal: what is being created across the
 * application, newest first.
 *
 * <p>Privacy boundary, deliberately hard-coded here: this only reads public content (titles, names,
 * categories, public post text) and moderation/finance queue rows. It never touches messages,
 * conversations, connections or the free-text of an application — so an administrator can see THAT
 * things are happening without being able to read anything private.
 *
 * <p>One small query per source rather than a single UNION: the tables use different collations, and
 * a UNION over them fails on mixed-collation string literals.
 */
@Service
public class AdminActivityService {

    static final int MAX_LIMIT = 100;

    private static final String NOT_ADMIN =
            "u.id NOT IN (SELECT user_id FROM user_security_roles WHERE role = 'ADMIN')";

    private record Source(String type, String sql) {}

    private static final List<Source> SOURCES = List.of(
            new Source("USER_JOINED",
                    "SELECT u.id AS actor_id, u.name AS actor_name, u.name AS label, u.id AS target_id, u.created_at AS occurred_at "
                            + "FROM users u WHERE " + NOT_ADMIN + " ORDER BY u.created_at DESC LIMIT ?"),
            new Source("STARTUP_CREATED",
                    "SELECT NULL AS actor_id, NULL AS actor_name, s.name AS label, s.id AS target_id, s.created_at AS occurred_at "
                            + "FROM startups s ORDER BY s.created_at DESC LIMIT ?"),
            new Source("IDEA_POSTED",
                    "SELECT u.id, u.name, i.title, i.id, i.created_at FROM ideas i JOIN users u ON u.id = i.creator_id "
                            + "ORDER BY i.created_at DESC LIMIT ?"),
            new Source("OPPORTUNITY_POSTED",
                    "SELECT u.id, u.name, o.title, o.id, o.created_at FROM opportunities o JOIN users u ON u.id = o.posted_by_user_id "
                            + "ORDER BY o.created_at DESC LIMIT ?"),
            new Source("EVENT_CREATED",
                    "SELECT u.id, u.name, e.title, e.id, e.created_at FROM events e JOIN users u ON u.id = e.organizer_user_id "
                            + "ORDER BY e.created_at DESC LIMIT ?"),
            new Source("GRANT_ADDED",
                    "SELECT u.id, u.name, g.name, g.id, g.created_at FROM grants g JOIN users u ON u.id = g.created_by_user_id "
                            + "ORDER BY g.created_at DESC LIMIT ?"),
            new Source("POST_PUBLISHED",
                    "SELECT u.id, u.name, COALESCE(LEFT(p.content, 100), p.type), p.id, p.created_at "
                            + "FROM posts p JOIN users u ON u.id = p.author_id ORDER BY p.created_at DESC LIMIT ?"),
            new Source("APPLICATION_SUBMITTED",
                    "SELECT u.id, u.name, o.title, o.id, a.created_at FROM opportunity_applicants a "
                            + "JOIN users u ON u.id = a.user_id JOIN opportunities o ON o.id = a.opportunity_id "
                            + "ORDER BY a.created_at DESC LIMIT ?"),
            new Source("REPORT_FILED",
                    "SELECT u.id, u.name, r.category, r.id, r.created_at FROM reports r JOIN users u ON u.id = r.reporter_id "
                            + "ORDER BY r.created_at DESC LIMIT ?"),
            new Source("WITHDRAWAL_REQUESTED",
                    "SELECT u.id, u.name, CONCAT(w.currency, ' ', FORMAT(w.amount_minor_units / 100, 2)), w.id, w.created_at "
                            + "FROM withdrawal_requests w JOIN users u ON u.id = w.user_id ORDER BY w.created_at DESC LIMIT ?"),
            new Source("INVESTOR_APPLICATION",
                    "SELECT u.id, u.name, i.investor_type, i.id, i.created_at FROM investor_activation_requests i "
                            + "JOIN users u ON u.id = i.requester_user_id ORDER BY i.created_at DESC LIMIT ?")
    );

    private final JdbcTemplate jdbcTemplate;

    public AdminActivityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<AdminActivityDto> recent(int limit) {
        int bounded = clampLimit(limit);
        List<List<AdminActivityDto>> perSource = new ArrayList<>(SOURCES.size());
        for (Source source : SOURCES) {
            perSource.add(jdbcTemplate.query(source.sql(),
                    (rs, i) -> new AdminActivityDto(
                            source.type(),
                            rs.getTimestamp(5).toInstant(),
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4)),
                    bounded));
        }
        return mergeLatest(perSource, bounded);
    }

    static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    static List<AdminActivityDto> mergeLatest(List<List<AdminActivityDto>> perSource, int limit) {
        return perSource.stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(AdminActivityDto::occurredAt).reversed())
                .limit(limit)
                .toList();
    }
}
