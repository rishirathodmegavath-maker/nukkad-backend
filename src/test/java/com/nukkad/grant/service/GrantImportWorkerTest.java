package com.nukkad.grant.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.grant.entity.Grant;
import com.nukkad.grant.entity.GrantImportBatch;
import com.nukkad.grant.entity.GrantImportIssue;
import com.nukkad.grant.entity.GrantImportIssueSeverity;
import com.nukkad.grant.entity.GrantImportStatus;
import com.nukkad.grant.entity.GrantProviderType;
import com.nukkad.grant.repository.GrantImportBatchRepository;
import com.nukkad.grant.repository.GrantImportIssueRepository;
import com.nukkad.grant.repository.GrantRepository;
import com.nukkad.startup.entity.StartupStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Calling {@link GrantImportWorker#processImportAsync} directly (not through a Spring proxy) runs it on the
 *  calling thread — exactly what a unit test wants; {@code @Async} only matters at runtime via DI. */
@ExtendWith(MockitoExtension.class)
class GrantImportWorkerTest {

    @Mock private GrantRepository grantRepository;
    @Mock private GrantImportBatchRepository grantImportBatchRepository;
    @Mock private GrantImportIssueRepository grantImportIssueRepository;
    @Mock private AuditService auditService;

    private GrantImportWorker worker() {
        return new GrantImportWorker(grantRepository, grantImportBatchRepository, grantImportIssueRepository, auditService);
    }

    private static GrantCsvRow validRow(int rowNumber, String name) {
        return new GrantCsvRow(rowNumber, name, "Some Provider", "Government", GrantProviderType.GOVERNMENT,
                "A grant", "Up to 10L", "Startups only", Set.of("Fintech"), Set.of(StartupStage.IDEA),
                "2026-12-31", java.time.Instant.parse("2026-12-31T18:29:59Z"), "https://example.com",
                List.of(), null);
    }

    private static GrantCsvRow hardErrorRow(int rowNumber) {
        return new GrantCsvRow(rowNumber, null, null, null, null, null, null, null, Set.of(), Set.of(),
                null, null, null, List.of(), "No grant/scheme name in this row");
    }

    @Test
    void aValidRowCreatesANewLiveGrantAttributedToTheImportingAdmin() {
        when(grantImportBatchRepository.findById("b1")).thenReturn(Optional.of(GrantImportBatch.builder().id("b1").build()));
        when(grantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        worker().processImportAsync("b1", List.of(validRow(1, "Startup India Seed Fund")), "admin1");

        ArgumentCaptor<Grant> captor = ArgumentCaptor.forClass(Grant.class);
        verify(grantRepository).saveAndFlush(captor.capture());
        Grant saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Startup India Seed Fund");
        assertThat(saved.getProviderType()).isEqualTo(GrantProviderType.GOVERNMENT);
        assertThat(saved.getCreatedByUserId()).isEqualTo("admin1");
        assertThat(saved.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(saved.getEligibleStages()).containsExactly(StartupStage.IDEA);

        ArgumentCaptor<GrantImportBatch> batchCaptor = ArgumentCaptor.forClass(GrantImportBatch.class);
        verify(grantImportBatchRepository, times(3)).save(batchCaptor.capture()); // markProcessing, final updateProgress, finish
        GrantImportBatch finalState = batchCaptor.getAllValues().get(batchCaptor.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(GrantImportStatus.COMPLETED);
        assertThat(finalState.getCreatedCount()).isEqualTo(1);
        assertThat(finalState.getProcessedRows()).isEqualTo(1);

        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_GRANT_IMPORTED), eq("GrantImportBatch"), eq("b1"),
                eq("internal:grant-import"), any());
    }

    @Test
    void aHardErrorRowIsSkippedAndRecordedAsAnErrorIssueWithoutPersistingAnything() {
        when(grantImportBatchRepository.findById("b1")).thenReturn(Optional.of(GrantImportBatch.builder().id("b1").build()));

        worker().processImportAsync("b1", List.of(hardErrorRow(3)), "admin1");

        verify(grantRepository, never()).saveAndFlush(any());
        ArgumentCaptor<GrantImportIssue> issueCaptor = ArgumentCaptor.forClass(GrantImportIssue.class);
        verify(grantImportIssueRepository).save(issueCaptor.capture());
        assertThat(issueCaptor.getValue().getSeverity()).isEqualTo(GrantImportIssueSeverity.ERROR);
        assertThat(issueCaptor.getValue().getRowNumber()).isEqualTo(3);

        ArgumentCaptor<GrantImportBatch> batchCaptor = ArgumentCaptor.forClass(GrantImportBatch.class);
        verify(grantImportBatchRepository, times(3)).save(batchCaptor.capture());
        GrantImportBatch finalState = batchCaptor.getAllValues().get(batchCaptor.getAllValues().size() - 1);
        assertThat(finalState.getSkippedCount()).isEqualTo(1);
        assertThat(finalState.getCreatedCount()).isZero();
    }

    @Test
    void aRowLevelWarningIsRecordedButTheRowStillImports() {
        when(grantImportBatchRepository.findById("b1")).thenReturn(Optional.of(GrantImportBatch.builder().id("b1").build()));
        when(grantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        GrantCsvRow warnedRow = new GrantCsvRow(1, "Some Grant", "Some Provider", "Bank", GrantProviderType.OTHER,
                null, null, null, Set.of(), Set.of(), null, null, "https://example.com",
                List.of("Unrecognized provider type \"Bank\" — defaulted to Other"), null);

        worker().processImportAsync("b1", List.of(warnedRow), "admin1");

        verify(grantRepository).saveAndFlush(any());
        ArgumentCaptor<GrantImportIssue> issueCaptor = ArgumentCaptor.forClass(GrantImportIssue.class);
        verify(grantImportIssueRepository).save(issueCaptor.capture());
        assertThat(issueCaptor.getValue().getSeverity()).isEqualTo(GrantImportIssueSeverity.WARNING);
    }

    @Test
    void aPersistenceFailureForOneRowDoesNotAbortTheRemainingRows() {
        when(grantImportBatchRepository.findById("b1")).thenReturn(Optional.of(GrantImportBatch.builder().id("b1").build()));
        when(grantRepository.saveAndFlush(any()))
                .thenThrow(new RuntimeException("DB constraint violation"))
                .thenAnswer(inv -> inv.getArgument(0));

        worker().processImportAsync("b1", List.of(validRow(1, "First Grant"), validRow(2, "Second Grant")), "admin1");

        verify(grantRepository, times(2)).saveAndFlush(any());

        ArgumentCaptor<GrantImportBatch> batchCaptor = ArgumentCaptor.forClass(GrantImportBatch.class);
        verify(grantImportBatchRepository, times(3)).save(batchCaptor.capture());
        GrantImportBatch finalState = batchCaptor.getAllValues().get(batchCaptor.getAllValues().size() - 1);
        assertThat(finalState.getCreatedCount()).isEqualTo(1);
        assertThat(finalState.getFailedCount()).isEqualTo(1);
        assertThat(finalState.getProcessedRows()).isEqualTo(2);
        assertThat(finalState.getStatus()).isEqualTo(GrantImportStatus.COMPLETED);
    }

    @Test
    void ifTheBatchVanishesBeforeProcessingStartsNothingIsProcessed() {
        when(grantImportBatchRepository.findById("gone")).thenReturn(Optional.empty());
        worker().processImportAsync("gone", List.of(validRow(1, "Some Grant")), "admin1");
        verify(grantRepository, never()).saveAndFlush(any());
        verify(auditService, never()).log(anyString(), any(), anyString(), anyString(), any(), any());
    }
}
