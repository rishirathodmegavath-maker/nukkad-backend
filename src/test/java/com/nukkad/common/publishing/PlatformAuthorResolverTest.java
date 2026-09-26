package com.nukkad.common.publishing;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.user.entity.AccountStatus;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Shared by every admin-create-content flow (Feed, Ideas, Startups, Opportunities, Grants, Events,
 *  Resources, Discussions) to decide who a piece of admin-created content really belongs to. */
@ExtendWith(MockitoExtension.class)
class PlatformAuthorResolverTest {

    @Mock private UserRepository userRepository;

    private PlatformAuthorResolver resolver() {
        return new PlatformAuthorResolver(userRepository);
    }

    @Test
    void blankOrNullEmailResolvesToTheAdminsOwnAccount() {
        assertThat(resolver().resolve("admin-1", null)).isEqualTo("admin-1");
        assertThat(resolver().resolve("admin-1", "   ")).isEqualTo("admin-1");
    }

    @Test
    void aRealActiveMemberEmailResolvesToTheirRealId() {
        when(userRepository.findByEmail("author@example.com")).thenReturn(Optional.of(
                User.builder().id("author-9").email("author@example.com").status(AccountStatus.ACTIVE).build()));

        assertThat(resolver().resolve("admin-1", "  Author@Example.com ")).isEqualTo("author-9");
    }

    @Test
    void anUnknownEmailIsRejected() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().resolve("admin-1", "nobody@example.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No member has that email address");
    }

    @Test
    void aSuspendedMembersEmailIsRejected() {
        when(userRepository.findByEmail("sus@example.com")).thenReturn(Optional.of(
                User.builder().id("sus-1").email("sus@example.com").status(AccountStatus.SUSPENDED).build()));

        assertThatThrownBy(() -> resolver().resolve("admin-1", "sus@example.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active");
    }
}
