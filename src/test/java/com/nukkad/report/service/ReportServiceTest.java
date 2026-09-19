package com.nukkad.report.service;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.feed.entity.Post;
import com.nukkad.feed.repository.PostRepository;
import com.nukkad.report.entity.Report;
import com.nukkad.report.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock private PostRepository postRepository;

    private ReportService service() {
        return new ReportService(reportRepository, postRepository);
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
        when(postRepository.findById("post1")).thenReturn(Optional.of(
                Post.builder().id("post1").authorId("realAuthor").content("spam content").build()));

        service().submit("reporter1", "someoneElseEntirely", "Spam", null, "post1");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getReportedUserId()).isEqualTo("realAuthor");
        assertThat(captor.getValue().getPostId()).isEqualTo("post1");
    }

    @Test
    void reportingAPostThatDoesntExistIsNotFound() {
        when(postRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().submit("reporter1", null, "Spam", null, "ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void cannotReportYourOwnPost() {
        when(postRepository.findById("post1")).thenReturn(Optional.of(
                Post.builder().id("post1").authorId("author1").content("x").build()));

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
}
