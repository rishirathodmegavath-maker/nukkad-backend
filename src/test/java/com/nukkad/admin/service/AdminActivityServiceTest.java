package com.nukkad.admin.service;

import com.nukkad.admin.dto.AdminActivityDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminActivityServiceTest {

    private static AdminActivityDto item(String type, String at) {
        return new AdminActivityDto(type, Instant.parse(at), null, null, "label", "id");
    }

    @Test
    void mergesEverySourceNewestFirstAndKeepsOnlyTheRequestedCount() {
        var merged = AdminActivityService.mergeLatest(List.of(
                List.of(item("USER_JOINED", "2026-09-19T10:00:00Z"), item("USER_JOINED", "2026-09-19T08:00:00Z")),
                List.of(item("IDEA_POSTED", "2026-09-19T09:00:00Z")),
                List.of(item("EVENT_CREATED", "2026-09-19T11:00:00Z"))), 3);

        assertThat(merged).extracting(AdminActivityDto::type)
                .containsExactly("EVENT_CREATED", "USER_JOINED", "IDEA_POSTED");
    }

    @Test
    void limitIsClampedToASaneRange() {
        assertThat(AdminActivityService.clampLimit(0)).isEqualTo(1);
        assertThat(AdminActivityService.clampLimit(-5)).isEqualTo(1);
        assertThat(AdminActivityService.clampLimit(50)).isEqualTo(50);
        assertThat(AdminActivityService.clampLimit(10_000)).isEqualTo(AdminActivityService.MAX_LIMIT);
    }
}
