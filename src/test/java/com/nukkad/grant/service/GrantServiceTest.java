package com.nukkad.grant.service;

import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.grant.dto.CreateGrantRequest;
import com.nukkad.grant.dto.GrantDto;
import com.nukkad.grant.dto.UpdateGrantRequest;
import com.nukkad.grant.entity.Grant;
import com.nukkad.grant.mapper.GrantMapper;
import com.nukkad.grant.repository.GrantRepository;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrantServiceTest {

    @Mock private GrantRepository grantRepository;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;

    private final GrantMapper grantMapper = new GrantMapper();

    private GrantService service() {
        return new GrantService(grantRepository, grantMapper, notificationService, auditService);
    }

    private CreateGrantRequest request(String applicationUrl) {
        return new CreateGrantRequest("Startup India Seed Fund", "Govt of India", "Government",
                "Seed funding for early-stage startups", "Up to ₹20L", "Must be DPIIT-recognized",
                List.of("AI", "Fintech"), List.of("Idea", "MVP"), null, applicationUrl);
    }

    private Grant grant(String id, String creatorId) {
        return Grant.builder().id(id).name("Grant " + id).provider("P").createdByUserId(creatorId)
                .moderationStatus(ModerationStatus.APPROVED).build();
    }

    @Test
    void createGrantNormalizesABareApplicationUrl() {
        when(grantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        GrantDto dto = service().createGrant("founder1", request("seedfund.startupindia.gov.in"));

        assertThat(dto.applicationUrl()).isEqualTo("https://seedfund.startupindia.gov.in");
        assertThat(dto.providerType()).isEqualTo("Government");
        assertThat(dto.eligibleSectors()).containsExactlyInAnyOrder("AI", "Fintech");
        assertThat(dto.eligibleStages()).containsExactlyInAnyOrder("Idea", "MVP");
        assertThat(dto.canManage()).isTrue();
    }

    @Test
    void createGrantRejectsAMalformedUrl() {
        assertThatThrownBy(() -> service().createGrant("founder1", request("not a url with spaces")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createGrantRejectsAnUnknownProviderType() {
        CreateGrantRequest bad = new CreateGrantRequest("X", "Y", "NotARealType", null, null, null, null, null, null, "example.com");
        assertThatThrownBy(() -> service().createGrant("founder1", bad)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void createGrantRejectsAnUnknownStage() {
        CreateGrantRequest bad = new CreateGrantRequest("X", "Y", "Government", null, null, null, null, List.of("NotAStage"), null, "example.com");
        assertThatThrownBy(() -> service().createGrant("founder1", bad)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void creatorCanUpdateTheirOwnGrant() {
        Grant grant = Grant.builder().id("g1").name("Old Name").provider("Old").createdByUserId("creator1").build();
        when(grantRepository.findById("g1")).thenReturn(Optional.of(grant));
        when(grantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        GrantDto dto = service().updateGrant("creator1", "g1", new UpdateGrantRequest(
                "New Name", null, null, null, null, null, null, null, null, null));

        assertThat(dto.name()).isEqualTo("New Name");
    }

    @Test
    void nonCreatorCannotUpdateOrDeleteTheGrant() {
        Grant grant = Grant.builder().id("g1").name("Name").provider("P").createdByUserId("creator1").build();
        when(grantRepository.findById("g1")).thenReturn(Optional.of(grant));

        assertThatThrownBy(() -> service().updateGrant("stranger1", "g1",
                new UpdateGrantRequest("Hijacked", null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> service().deleteGrant("stranger1", "g1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void publicGetterHidesAPendingGrantFromEveryoneButItsCreator() {
        Grant grant = grant("g1", "creator1");
        grant.setModerationStatus(ModerationStatus.PENDING);
        when(grantRepository.findById("g1")).thenReturn(Optional.of(grant));

        assertThatThrownBy(() -> service().getGrant("g1", "someoneElse")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publicGetterStillShowsAPendingGrantToItsOwnCreator() {
        Grant grant = grant("g1", "creator1");
        grant.setModerationStatus(ModerationStatus.PENDING);
        when(grantRepository.findById("g1")).thenReturn(Optional.of(grant));

        GrantDto dto = service().getGrant("g1", "creator1");

        assertThat(dto.moderationStatus()).isEqualTo("PENDING");
    }

    @Test
    void approvingAPendingGrantLogsAuditAndNotifiesTheCreator() {
        Grant grant = grant("g1", "creator1");
        grant.setModerationStatus(ModerationStatus.PENDING);
        when(grantRepository.findById("g1")).thenReturn(Optional.of(grant));
        when(grantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        GrantDto dto = service().reviewModeration("admin1", "g1", true, null, "127.0.0.1");

        assertThat(dto.moderationStatus()).isEqualTo("APPROVED");
        verify(auditService).log(eq("admin1"), any(), eq("Grant"), eq("g1"), any(), any());
        verify(notificationService).notify(eq("creator1"), eq(NotificationType.grant), anyString(), anyString(), eq("g1"), eq("admin1"));
    }

    @Test
    void rejectingAPendingGrantRequiresAReasonAndRecordsIt() {
        Grant grant = grant("g1", "creator1");
        grant.setModerationStatus(ModerationStatus.PENDING);
        when(grantRepository.findById("g1")).thenReturn(Optional.of(grant));

        assertThatThrownBy(() -> service().reviewModeration("admin1", "g1", false, null, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);

        when(grantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        GrantDto dto = service().reviewModeration("admin1", "g1", false, "broken link", "127.0.0.1");
        assertThat(dto.moderationStatus()).isEqualTo("REJECTED");
        assertThat(dto.rejectionReason()).isEqualTo("broken link");
    }

    @Test
    void anAlreadyReviewedGrantCannotBeReviewedAgain() {
        Grant grant = grant("g1", "creator1");
        when(grantRepository.findById("g1")).thenReturn(Optional.of(grant));

        assertThatThrownBy(() -> service().reviewModeration("admin1", "g1", true, null, "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }
}
