package com.nukkad.investor.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.investor.entity.Investor;
import com.nukkad.investor.entity.InvestorImportBatch;
import com.nukkad.investor.entity.InvestorImportIssue;
import com.nukkad.investor.entity.InvestorImportIssueSeverity;
import com.nukkad.investor.entity.InvestorImportStatus;
import com.nukkad.investor.entity.InvestorType;
import com.nukkad.investor.repository.InvestorImportBatchRepository;
import com.nukkad.investor.repository.InvestorImportIssueRepository;
import com.nukkad.investor.repository.InvestorRepository;
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

/** Calling {@link InvestorImportWorker#processImportAsync} directly (not through a Spring proxy) runs it on
 *  the calling thread — exactly what a unit test wants; {@code @Async} only matters at runtime via DI. */
@ExtendWith(MockitoExtension.class)
class InvestorImportWorkerTest {

    @Mock private InvestorRepository investorRepository;
    @Mock private InvestorImportBatchRepository investorImportBatchRepository;
    @Mock private InvestorImportIssueRepository investorImportIssueRepository;
    @Mock private AuditService auditService;

    private InvestorImportWorker worker() {
        return new InvestorImportWorker(investorRepository, investorImportBatchRepository, investorImportIssueRepository, auditService);
    }

    private static InvestorCsvRow validRow(int rowNumber, String externalId, String name) {
        return new InvestorCsvRow(rowNumber, externalId, name, "VC", InvestorType.VC, "Backs founders", "Bangalore",
                "India", "https://x.vc", "x.vc", Set.of("AI"), Set.of(), 10, 2, Set.of(), null, null, null, null,
                null, null, null, null, List.of(), null);
    }

    private static InvestorCsvRow hardErrorRow(int rowNumber) {
        return new InvestorCsvRow(rowNumber, null, null, null, null, null, null, null, null, null, Set.of(), Set.of(),
                null, null, Set.of(), null, null, null, null, null, null, null, null, List.of(), "No company/investor name in this row");
    }

    @Test
    void aRowWithNoExistingMatchCreatesANewInvestor() {
        InvestorImportBatch batch = InvestorImportBatch.builder().id("b1").status(InvestorImportStatus.PENDING).build();
        when(investorImportBatchRepository.findById("b1")).thenReturn(Optional.of(batch));
        when(investorRepository.findByExternalSourceId("src-1")).thenReturn(Optional.empty());
        when(investorRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        worker().processImportAsync("b1", List.of(validRow(1, "src-1", "Acme Capital")), "admin1", "127.0.0.1");

        ArgumentCaptor<Investor> captor = ArgumentCaptor.forClass(Investor.class);
        verify(investorRepository).saveAndFlush(captor.capture());
        Investor saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Acme Capital");
        assertThat(saved.getExternalSourceId()).isEqualTo("src-1");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isVisible()).isTrue();
        assertThat(saved.getCreatedByAdminId()).isEqualTo("admin1");
        assertThat(saved.getSourceBatchId()).isEqualTo("b1");

        ArgumentCaptor<InvestorImportBatch> batchCaptor = ArgumentCaptor.forClass(InvestorImportBatch.class);
        verify(investorImportBatchRepository, times(3)).save(batchCaptor.capture()); // markProcessing, final updateProgress, finish
        InvestorImportBatch finalState = batchCaptor.getAllValues().get(batchCaptor.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(InvestorImportStatus.COMPLETED);
        assertThat(finalState.getCreatedCount()).isEqualTo(1);
        assertThat(finalState.getProcessedRows()).isEqualTo(1);

        verify(auditService).log(eq("admin1"), eq(AuditAction.ADMIN_INVESTOR_IMPORTED), eq("InvestorImportBatch"), eq("b1"),
                eq("127.0.0.1"), any());
    }

    @Test
    void aRowWhoseExternalIdAlreadyExistsUpdatesInPlaceAndNeverTouchesAdminOwnedFields() {
        when(investorImportBatchRepository.findById("b1")).thenReturn(Optional.of(InvestorImportBatch.builder().id("b1").build()));
        Investor existing = Investor.builder().id("inv1").externalSourceId("src-1").name("Old Name")
                .investorType(InvestorType.OTHER).active(false).visible(false).chequeMin(500_000L).chequeMax(1_000_000L)
                .stages(new java.util.HashSet<>(Set.of("Series A"))).createdByAdminId("admin0").build();
        when(investorRepository.findByExternalSourceId("src-1")).thenReturn(Optional.of(existing));
        when(investorRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        worker().processImportAsync("b1", List.of(validRow(1, "src-1", "New Name")), "admin1", null);

        ArgumentCaptor<Investor> captor = ArgumentCaptor.forClass(Investor.class);
        verify(investorRepository).saveAndFlush(captor.capture());
        Investor saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("inv1");
        assertThat(saved.getName()).isEqualTo("New Name");
        // Admin-owned fields untouched by the CSV sync:
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.isVisible()).isFalse();
        assertThat(saved.getChequeMin()).isEqualTo(500_000L);
        assertThat(saved.getStages()).containsExactly("Series A");
        assertThat(saved.getCreatedByAdminId()).isEqualTo("admin0");
    }

    @Test
    void aHardErrorRowIsSkippedAndRecordedAsAnErrorIssue() {
        when(investorImportBatchRepository.findById("b1")).thenReturn(Optional.of(InvestorImportBatch.builder().id("b1").build()));

        worker().processImportAsync("b1", List.of(hardErrorRow(3)), "admin1", null);

        verify(investorRepository, never()).saveAndFlush(any());
        ArgumentCaptor<InvestorImportIssue> issueCaptor = ArgumentCaptor.forClass(InvestorImportIssue.class);
        verify(investorImportIssueRepository).save(issueCaptor.capture());
        assertThat(issueCaptor.getValue().getSeverity()).isEqualTo(InvestorImportIssueSeverity.ERROR);
        assertThat(issueCaptor.getValue().getRowNumber()).isEqualTo(3);

        ArgumentCaptor<InvestorImportBatch> batchCaptor = ArgumentCaptor.forClass(InvestorImportBatch.class);
        verify(investorImportBatchRepository, times(3)).save(batchCaptor.capture());
        InvestorImportBatch finalState = batchCaptor.getAllValues().get(batchCaptor.getAllValues().size() - 1);
        assertThat(finalState.getSkippedCount()).isEqualTo(1);
        assertThat(finalState.getCreatedCount()).isZero();
    }

    @Test
    void aRowLevelWarningIsRecordedButTheRowStillImports() {
        when(investorImportBatchRepository.findById("b1")).thenReturn(Optional.of(InvestorImportBatch.builder().id("b1").build()));
        when(investorRepository.findByExternalSourceId(anyString())).thenReturn(Optional.empty());
        when(investorRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        InvestorCsvRow warnedRow = new InvestorCsvRow(1, "src-2", "Mystery Capital", "Space Pirates", null,
                null, null, null, null, null, Set.of(), Set.of(), null, null, Set.of(), null, null, null, null,
                null, null, null, null, List.of("Unrecognized investor type \"Space Pirates\" — defaulted to Other"), null);

        worker().processImportAsync("b1", List.of(warnedRow), "admin1", null);

        verify(investorRepository).saveAndFlush(any());
        ArgumentCaptor<InvestorImportIssue> issueCaptor = ArgumentCaptor.forClass(InvestorImportIssue.class);
        verify(investorImportIssueRepository).save(issueCaptor.capture());
        assertThat(issueCaptor.getValue().getSeverity()).isEqualTo(InvestorImportIssueSeverity.WARNING);
    }

    @Test
    void ifTheBatchVanishesBeforeProcessingStartsNothingIsProcessed() {
        when(investorImportBatchRepository.findById("gone")).thenReturn(Optional.empty());
        worker().processImportAsync("gone", List.of(validRow(1, "s1", "Acme")), "admin1", null);
        verify(investorRepository, never()).saveAndFlush(any());
        verify(auditService, never()).log(anyString(), any(), anyString(), anyString(), any(), any());
    }
}
