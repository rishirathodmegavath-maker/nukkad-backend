package com.nukkad.report.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.feed.service.FeedService;
import com.nukkad.report.entity.Report;
import com.nukkad.report.entity.ReportStatus;
import com.nukkad.report.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private FeedService feedService;

    private ReportService service() {
        return new ReportService(reportRepository, feedService);
    }

    @Test
    void reportingAUserDirectlyStillWorksUnchanged() {
        service().submit("reporter1", "reported1", "Spam", null, null);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getReportedUserId()).isEqualTo("reported1");
        assertThat(captor.getValue().getPostId()).isNull();
    }

    @Test
    void reportingAPostResolvesTheReportedUserFromThePostsOwnAuthorNeverFromTheClient() {
        when(feedService.requireVisibleAuthorId("reporter1", "post1")).thenReturn("realAuthor");

        service().submit("reporter1", "someoneElseEntirely", "Spam", null, "post1");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getReportedUserId()).isEqualTo("realAuthor");
        assertThat(captor.getValue().getPostId()).isEqualTo("post1");
    }

    @Test
    void reportingAPostThatDoesntExistIsNotFound() {
        when(feedService.requireVisibleAuthorId("reporter1", "ghost"))
                .thenThrow(new ResourceNotFoundException("Post not found: ghost"));

        assertThatThrownBy(() -> service().submit("reporter1", null, "Spam", null, "ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void reportingAPostOutsideTheReportersVisibilityIsNotFoundJustLikeANonexistentOne() {
        // A connections-only post from a stranger, or any other post the reporter can't otherwise
        // see, must fail the exact same way a nonexistent postId does -- never a different response
        // that would let "report" be used to probe whether such a post exists.
        when(feedService.requireVisibleAuthorId("reporter1", "hidden-post"))
                .thenThrow(new ResourceNotFoundException("Post not found: hidden-post"));

        assertThatThrownBy(() -> service().submit("reporter1", null, "Spam", null, "hidden-post"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void cannotReportYourOwnPost() {
        when(feedService.requireVisibleAuthorId("author1", "post1")).thenReturn("author1");

        assertThatThrownBy(() -> service().submit("author1", null, "Spam", null, "post1"))
                .isInstanceOf(BadRequestException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void submittingWithNeitherReportedUserNorPostIsRejected() {
        assertThatThrownBy(() -> service().submit("reporter1", null, "Spam", null, null))
                .isInstanceOf(BadRequestException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void cannotReportYourself() {
        assertThatThrownBy(() -> service().submit("user1", "user1", "Spam", null, null))
                .isInstanceOf(BadRequestException.class);
        verify(reportRepository, never()).save(any());
    }

    // ---- duplicate reports don't pile up in the moderation queue ----

    @Test
    void reportingTheSamePostAgainWhileTheFirstReportIsStillOpenIsASilentNoOp() {
        when(feedService.requireVisibleAuthorId("reporter1", "post1")).thenReturn("author1");
        Report existing = Report.builder().id("r0").reporterId("reporter1").reportedUserId("author1")
                .postId("post1").status(ReportStatus.OPEN).build();
        when(reportRepository.findByReporterIdAndStatus("reporter1", ReportStatus.OPEN)).thenReturn(List.of(existing));

        service().submit("reporter1", null, "Spam", null, "post1");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void reportingTheSameUserDirectlyAgainWhileTheFirstReportIsStillOpenIsASilentNoOp() {
        Report existing = Report.builder().id("r0").reporterId("reporter1").reportedUserId("reported1")
                .status(ReportStatus.OPEN).build();
        when(reportRepository.findByReporterIdAndStatus("reporter1", ReportStatus.OPEN)).thenReturn(List.of(existing));

        service().submit("reporter1", "reported1", "Spam", null, null);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void aNewReportIsAllowedOnceThePriorOneWasAlreadyResolved() {
        when(feedService.requireVisibleAuthorId("reporter1", "post1")).thenReturn("author1");
        // findByReporterIdAndStatus(..., OPEN) only ever returns OPEN rows, so a resolved prior
        // report against the same post simply isn't in this list -- nothing to mock returning it.
        when(reportRepository.findByReporterIdAndStatus("reporter1", ReportStatus.OPEN)).thenReturn(List.of());

        service().submit("reporter1", null, "Spam", null, "post1");

        verify(reportRepository).save(any());
    }

    @Test
    void reportingADifferentPostWhileAnotherIsOpenStillGoesThrough() {
        when(feedService.requireVisibleAuthorId("reporter1", "post2")).thenReturn("author2");
        Report existing = Report.builder().id("r0").reporterId("reporter1").reportedUserId("author1")
                .postId("post1").status(ReportStatus.OPEN).build();
        when(reportRepository.findByReporterIdAndStatus("reporter1", ReportStatus.OPEN)).thenReturn(List.of(existing));

        service().submit("reporter1", null, "Spam", null, "post2");

        verify(reportRepository).save(any());
    }

    // ---- resolve ----

    private Report openReport() {
        return Report.builder().id("r1").reporterId("reporter1").reportedUserId("reported1")
                .category("Spam").status(ReportStatus.OPEN).build();
    }

    @Test
    void resolvingAnOpenReportTransitionsItAndRecordsTheReviewer() {
        Report report = openReport();
        when(reportRepository.findByIdForUpdate("r1")).thenReturn(Optional.of(report));
        when(reportRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Report result = service().resolve("admin1", "r1", ReportStatus.RESOLVED, "handled");

        assertThat(result.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(result.getResolvedByUserId()).isEqualTo("admin1");
        assertThat(result.getResolutionNote()).isEqualTo("handled");
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    void resolvingAnAlreadyReviewedReportIsRejected() {
        Report report = openReport();
        report.setStatus(ReportStatus.DISMISSED);
        when(reportRepository.findByIdForUpdate("r1")).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service().resolve("admin1", "r1", ReportStatus.RESOLVED, "too late"))
                .isInstanceOf(ConflictException.class);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolveUsesTheLockedReadNotAPlainFindById() {
        // Regression guard: a plain findById lets two concurrent resolve calls both observe
        // status == OPEN before either commits, so both succeed instead of one correctly hitting
        // the "already reviewed" conflict above. Must go through the row-locked read.
        Report report = openReport();
        when(reportRepository.findByIdForUpdate("r1")).thenReturn(Optional.of(report));
        when(reportRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().resolve("admin1", "r1", ReportStatus.RESOLVED, "handled");

        verify(reportRepository, never()).findById(any());
    }
}
