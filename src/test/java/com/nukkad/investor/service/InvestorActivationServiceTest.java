package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.investor.dto.InvestorActivationRequestDto;
import com.nukkad.investor.dto.SubmitInvestorActivationRequest;
import com.nukkad.investor.entity.InvestorActivationRequest;
import com.nukkad.investor.entity.InvestorActivationStatus;
import com.nukkad.investor.entity.InvestorType;
import com.nukkad.investor.mapper.InvestorMapper;
import com.nukkad.investor.repository.InvestorActivationRequestRepository;
import com.nukkad.investor.repository.InvestorProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestorActivationServiceTest {

    @Mock private InvestorActivationRequestRepository activationRequestRepository;
    @Mock private InvestorProfileRepository investorProfileRepository;
    private final InvestorMapper investorMapper = new InvestorMapper();

    private InvestorActivationService service() {
        return new InvestorActivationService(activationRequestRepository, investorProfileRepository, investorMapper);
    }

    private SubmitInvestorActivationRequest request() {
        return new SubmitInvestorActivationRequest("Angel", "Acme Ventures", "Early-stage B2B",
                Set.of("Fintech"), Set.of("Idea"), Set.of("India"), 50000L, 500000L, 5, "https://acme.vc");
    }

    @Test
    void submittingAnApplicationSavesItAsPending() {
        when(activationRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        InvestorActivationRequestDto dto = service().submit("user1", request());

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.investorType()).isEqualTo("Angel");
        ArgumentCaptor<InvestorActivationRequest> captor = ArgumentCaptor.forClass(InvestorActivationRequest.class);
        org.mockito.Mockito.verify(activationRequestRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRequesterUserId()).isEqualTo("user1");
        assertThat(captor.getValue().getStatus()).isEqualTo(InvestorActivationStatus.PENDING);
    }

    @Test
    void cannotApplyIfYouAlreadyHaveAnInvestorProfile() {
        when(investorProfileRepository.existsByUserId("user1")).thenReturn(true);

        assertThatThrownBy(() -> service().submit("user1", request())).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotSubmitASecondApplicationWhileOneIsStillPending() {
        InvestorActivationRequest pending = InvestorActivationRequest.builder()
                .id("r1").requesterUserId("user1").investorType(InvestorType.ANGEL)
                .status(InvestorActivationStatus.PENDING).build();
        when(activationRequestRepository.findTopByRequesterUserIdOrderByCreatedAtDesc("user1"))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service().submit("user1", request())).isInstanceOf(ConflictException.class);
    }

    @Test
    void canReapplyAfterAPreviousRejection() {
        InvestorActivationRequest rejected = InvestorActivationRequest.builder()
                .id("r1").requesterUserId("user1").investorType(InvestorType.ANGEL)
                .status(InvestorActivationStatus.REJECTED).build();
        when(activationRequestRepository.findTopByRequesterUserIdOrderByCreatedAtDesc("user1"))
                .thenReturn(Optional.of(rejected));
        when(activationRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        InvestorActivationRequestDto dto = service().submit("user1", request());

        assertThat(dto.status()).isEqualTo("PENDING");
    }

    @Test
    void unknownInvestorTypeIsRejected() {
        assertThatThrownBy(() -> service().submit("user1", new SubmitInvestorActivationRequest(
                "NotARealType", null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void gettingYourOwnApplicationStatusWithNoneSubmittedIsNotFound() {
        when(activationRequestRepository.findTopByRequesterUserIdOrderByCreatedAtDesc("user1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMine("user1")).isInstanceOf(ResourceNotFoundException.class);
    }
}
