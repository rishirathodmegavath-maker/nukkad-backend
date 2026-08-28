package com.nukkad.investor.service;

import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.investor.dto.CreateInvestorProfileRequest;
import com.nukkad.investor.dto.InvestorProfileDto;
import com.nukkad.investor.dto.UpdateInvestorProfileRequest;
import com.nukkad.investor.entity.InvestorProfile;
import com.nukkad.investor.entity.InvestorType;
import com.nukkad.investor.mapper.InvestorMapper;
import com.nukkad.investor.repository.InvestorProfileRepository;
import com.nukkad.user.entity.SecurityRole;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers investor-profile self-serve creation (+ role grant), ownership authorization, and update. */
@ExtendWith(MockitoExtension.class)
class InvestorProfileServiceTest {

    @Mock private InvestorProfileRepository investorProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    private final InvestorMapper investorMapper = new InvestorMapper();

    private InvestorProfileService service() {
        return new InvestorProfileService(investorProfileRepository, userRepository, userService, investorMapper);
    }

    private CreateInvestorProfileRequest createRequest() {
        return new CreateInvestorProfileRequest("VC", "Sequoia", "Early-stage B2B SaaS", Set.of("SaaS"), Set.of("Idea"), Set.of("India"), 50000L, 500000L, 12, "https://sequoia.com");
    }

    private InvestorProfile profile(String id, String userId) {
        return InvestorProfile.builder().id(id).userId(userId).investorType(InvestorType.VC)
                .sectors(new HashSet<>()).stages(new HashSet<>()).geographies(new HashSet<>()).build();
    }

    @Test
    void creatingAProfileGrantsTheInvestorRole() {
        when(investorProfileRepository.existsByUserId("u1")).thenReturn(false);
        when(investorProfileRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        User user = User.builder().id("u1").securityRoles(new HashSet<>(Set.of(SecurityRole.USER))).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InvestorProfileDto dto = service().create("u1", createRequest());

        assertThat(dto.userId()).isEqualTo("u1");
        assertThat(dto.canManage()).isTrue();
        assertThat(user.getSecurityRoles()).contains(SecurityRole.INVESTOR);
    }

    @Test
    void cannotCreateASecondProfileForTheSameUser() {
        when(investorProfileRepository.existsByUserId("u1")).thenReturn(true);

        assertThatThrownBy(() -> service().create("u1", createRequest())).isInstanceOf(ConflictException.class);

        verify(investorProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void ownerCanUpdateTheirProfile() {
        InvestorProfile existing = profile("p1", "u1");
        when(investorProfileRepository.findById("p1")).thenReturn(Optional.of(existing));
        when(investorProfileRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        InvestorProfileDto dto = service().update("u1", "p1",
                new UpdateInvestorProfileRequest(null, "New Firm", null, null, null, null, null, null, null, null));

        assertThat(dto.firmName()).isEqualTo("New Firm");
    }

    @Test
    void nonOwnerCannotUpdateAnotherUsersProfile() {
        InvestorProfile existing = profile("p1", "u1");
        when(investorProfileRepository.findById("p1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().update("u2", "p1",
                new UpdateInvestorProfileRequest(null, "Hijacked", null, null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);

        verify(investorProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonOwnerCannotDeleteAnotherUsersProfile() {
        InvestorProfile existing = profile("p1", "u1");
        when(investorProfileRepository.findById("p1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().delete("u2", "p1")).isInstanceOf(ForbiddenException.class);

        verify(investorProfileRepository, never()).delete(any(InvestorProfile.class));
    }

    @Test
    void deletingOwnProfileRevokesTheInvestorRole() {
        InvestorProfile existing = profile("p1", "u1");
        when(investorProfileRepository.findById("p1")).thenReturn(Optional.of(existing));
        User user = User.builder().id("u1").securityRoles(new HashSet<>(Set.of(SecurityRole.USER, SecurityRole.INVESTOR))).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().delete("u1", "p1");

        verify(investorProfileRepository).delete(existing);
        assertThat(user.getSecurityRoles()).doesNotContain(SecurityRole.INVESTOR);
    }
}
