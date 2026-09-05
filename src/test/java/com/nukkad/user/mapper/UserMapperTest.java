package com.nukkad.user.mapper;

import com.nukkad.user.dto.ProfileSections;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for a real vulnerability found in a security audit: {@code UserDto.email}
 * was always populated regardless of who was asking, leaking every user's real email address to
 * any other authenticated user via profile lookups, search/browse listings, connections lists,
 * and event attendee lists — none of which the frontend ever renders it from, since it isn't
 * meant to be visible to anyone but the account owner.
 */
class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    private User user() {
        return User.builder()
                .id("u1")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .passwordHash("hash")
                .securityRoles(new HashSet<>(Set.of(SecurityRole.USER)))
                .build();
    }

    @Test
    void selfAccountViewIncludesEmail() {
        UserDto dto = mapper.toDto(user());
        assertThat(dto.email()).isEqualTo("ada@example.com");
    }

    @Test
    void publicDtoForAnotherUserNeverIncludesEmail() {
        UserDto dto = mapper.toPublicDto(user());
        assertThat(dto.email()).isNull();
    }

    @Test
    void listViewMapperNeverIncludesEmail() {
        UserDto dto = mapper.toDto(user(), "NONE", false);
        assertThat(dto.email()).isNull();
    }

    @Test
    void fullProfileMapperOmitsEmailWhenNotSelf() {
        UserDto dto = mapper.toDto(user(), "NONE", false, ProfileSections.empty(), 50, List.of(), List.of(), true, false);
        assertThat(dto.email()).isNull();
    }

    @Test
    void fullProfileMapperIncludesEmailWhenViewingSelf() {
        UserDto dto = mapper.toDto(user(), null, null, ProfileSections.empty(), 50, List.of(), List.of(), true, true);
        assertThat(dto.email()).isEqualTo("ada@example.com");
    }
}
