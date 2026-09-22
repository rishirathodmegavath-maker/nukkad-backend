package com.nukkad.investor.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.common.storage.FileStorageService;
import com.nukkad.investor.dto.InvestorImportBatchDto;
import com.nukkad.investor.dto.InvestorImportPreviewDto;
import com.nukkad.investor.entity.InvestorImportBatch;
import com.nukkad.investor.entity.InvestorImportStatus;
import com.nukkad.investor.repository.InvestorImportBatchRepository;
import com.nukkad.investor.repository.InvestorImportIssueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestorImportServiceTest {

    private final InvestorCsvParser investorCsvParser = new InvestorCsvParser();
    @Mock private InvestorImportBatchRepository investorImportBatchRepository;
    @Mock private InvestorImportIssueRepository investorImportIssueRepository;
    @Mock private InvestorImportWorker investorImportWorker;
    @Mock private FileStorageService fileStorageService;

    private InvestorImportService service() {
        return new InvestorImportService(investorCsvParser, investorImportBatchRepository, investorImportIssueRepository,
                investorImportWorker, fileStorageService);
    }

    private static MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "investors.csv", "text/csv", content.getBytes());
    }

    @Test
    void previewParsesButSavesNothing() {
        String content = "company_name,investor_type\nAcme,VC\nBeta,Angel\n";
        InvestorImportPreviewDto preview = service().preview(csvFile(content));

        assertThat(preview.totalRows()).isEqualTo(2);
        assertThat(preview.sampleRows()).hasSize(2);
        assertThat(preview.sampleRows().get(0).name()).isEqualTo("Acme");
        verify(investorImportBatchRepository, never()).save(any());
        verify(investorImportWorker, never()).processImportAsync(anyString(), any(), anyString(), any());
    }

    @Test
    void previewCapsTheSampleAtTenRowsEvenForALargerFile() {
        StringBuilder content = new StringBuilder("company_name,investor_type\n");
        for (int i = 0; i < 25; i++) content.append("Investor ").append(i).append(",VC\n");
        InvestorImportPreviewDto preview = service().preview(csvFile(content.toString()));
        assertThat(preview.totalRows()).isEqualTo(25);
        assertThat(preview.sampleRows()).hasSize(10);
    }

    @Test
    void startImportCreatesAPendingBatchAndHandsOffToTheAsyncWorker() {
        when(fileStorageService.storeResourceFile(any(), eq("investor-imports"))).thenReturn("https://cdn.example/investors.csv");
        when(investorImportBatchRepository.saveAndFlush(any())).thenAnswer(inv -> {
            InvestorImportBatch b = inv.getArgument(0);
            b.setId("batch1");
            b.setCreatedAt(java.time.Instant.now());
            return b;
        });

        String content = "company_name,investor_type\nAcme,VC\nBeta,Angel\n";
        InvestorImportBatchDto dto = service().startImport("admin1", csvFile(content), "127.0.0.1");

        assertThat(dto.id()).isEqualTo("batch1");
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.totalRows()).isEqualTo(2);
        verify(investorImportWorker).processImportAsync(eq("batch1"), any(), eq("admin1"), eq("127.0.0.1"));
    }

    @Test
    void startImportProceedsEvenWhenStoringTheOriginalFileFails() {
        when(fileStorageService.storeResourceFile(any(), eq("investor-imports"))).thenThrow(new RuntimeException("S3 down"));
        when(investorImportBatchRepository.saveAndFlush(any())).thenAnswer(inv -> {
            InvestorImportBatch b = inv.getArgument(0);
            b.setId("batch1");
            return b;
        });

        InvestorImportBatchDto dto = service().startImport("admin1", csvFile("company_name\nAcme\n"), null);

        assertThat(dto.id()).isEqualTo("batch1");
        verify(investorImportWorker).processImportAsync(eq("batch1"), any(), eq("admin1"), isNull());
    }

    @Test
    void startImportRejectsAFileWithNoDataRows() {
        assertThatThrownBy(() -> service().startImport("admin1", csvFile("company_name,investor_type\n"), null))
                .isInstanceOf(BadRequestException.class);
        verify(investorImportBatchRepository, never()).saveAndFlush(any());
    }

    @Test
    void gettingAMissingBatchIs404() {
        when(investorImportBatchRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().getBatch("ghost")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listingIssuesForAMissingBatchIs404() {
        when(investorImportBatchRepository.existsById("ghost")).thenReturn(false);
        assertThatThrownBy(() -> service().listIssues("ghost", 0, 20)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aBrokenFileNeverEvenReachesTheAsyncWorker() {
        assertThatThrownBy(() -> service().startImport("admin1", csvFile("investor_type\nVC\n"), null))
                .isInstanceOf(BadRequestException.class);
        verify(investorImportWorker, never()).processImportAsync(anyString(), any(), anyString(), any());
    }

    @Test
    void aPendingBatchDtoStartsAtZeroProgress() {
        InvestorImportBatch batch = InvestorImportBatch.builder().id("b1").status(InvestorImportStatus.PENDING)
                .totalRows(10).processedRows(0).build();
        when(investorImportBatchRepository.findById("b1")).thenReturn(Optional.of(batch));
        InvestorImportBatchDto dto = service().getBatch("b1");
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.processedRows()).isZero();
        assertThat(dto.totalRows()).isEqualTo(10);
    }
}
