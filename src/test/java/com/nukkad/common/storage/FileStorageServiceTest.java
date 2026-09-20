package com.nukkad.common.storage;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                "test-bucket", "auto", "https://r2.example.com", true, "https://cdn.example.com");
        fileStorageService = new FileStorageService(s3Client, properties);
    }

    @Test
    void storingAValidImageUploadsItAndReturnsAPublicUrl() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "content".getBytes());

        String url = fileStorageService.storeImage(file, "avatars");

        assertThat(url).startsWith("https://cdn.example.com/avatars/").endsWith(".png");
    }

    @Test
    void storingAnImageWithADisallowedContentTypeIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> fileStorageService.storeImage(file, "avatars"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PNG, JPEG, WEBP or GIF");
    }

    @Test
    void storingAnEmptyImageFileIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.storeImage(file, "avatars"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No file was uploaded");
    }

    @Test
    void storingANullImageFileIsRejected() {
        assertThatThrownBy(() -> fileStorageService.storeImage(null, "avatars"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No file was uploaded");
    }

    @Test
    void storingAValidVideoAsMediaUploadsItAndReturnsUrlAndKind() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", "content".getBytes());

        FileStorageService.StoredMedia stored = fileStorageService.storeMedia(file, "feed");

        assertThat(stored.url()).startsWith("https://cdn.example.com/feed/").endsWith(".mp4");
        assertThat(stored.kind()).isEqualTo(FileStorageService.AttachmentKind.VIDEO);
    }

    @Test
    void storingAValidPdfAsMediaUploadsItAndReturnsUrlAndKind() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", "content".getBytes());

        FileStorageService.StoredMedia stored = fileStorageService.storeMedia(file, "feed");

        assertThat(stored.url()).startsWith("https://cdn.example.com/feed/").endsWith(".pdf");
        assertThat(stored.kind()).isEqualTo(FileStorageService.AttachmentKind.PDF);
    }

    @Test
    void storingMediaWithADisallowedContentTypeIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "archive.zip", "application/zip", "content".getBytes());

        assertThatThrownBy(() -> fileStorageService.storeMedia(file, "feed"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("images, videos");
    }

    @Test
    void storingAnEmptyMediaFileIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.mp4", "video/mp4", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.storeMedia(file, "feed"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No file was uploaded");
    }

    // ---- resource library files (admin uploads): documents allowed, executable/markup types never ----

    private PutObjectRequest storeResourceAndCaptureRequest(MockMultipartFile file) {
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        String url = fileStorageService.storeResourceFile(file, "resources");
        assertThat(url).startsWith("https://cdn.example.com/resources/");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        return captor.getValue();
    }

    @Test
    void aWordDocumentCanBeStoredForTheResourceLibraryAndGetsTheServersContentType() {
        // Browsers often send application/octet-stream for Office files — the extension decides, not the claim.
        MockMultipartFile file = new MockMultipartFile("file", "Term Sheet.docx", "application/octet-stream", "content".getBytes());

        PutObjectRequest request = storeResourceAndCaptureRequest(file);

        assertThat(request.key()).startsWith("resources/").endsWith(".docx");
        assertThat(request.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void aClientCannotMislabelAResourceFileAsSomethingElse() {
        MockMultipartFile file = new MockMultipartFile("file", "deck.PDF", "text/html", "content".getBytes());

        PutObjectRequest request = storeResourceAndCaptureRequest(file);

        assertThat(request.key()).endsWith(".pdf");
        assertThat(request.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void everyDocumentAndMediaTypeTheLibraryPromisesIsAccepted() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        for (String name : new String[]{"a.pdf", "a.doc", "a.docx", "a.xls", "a.xlsx", "a.ppt", "a.pptx", "a.csv", "a.txt",
                "a.zip", "a.png", "a.jpg", "a.jpeg", "a.webp", "a.gif", "a.mp4", "a.webm", "a.mov"}) {
            String url = fileStorageService.storeResourceFile(new MockMultipartFile("file", name, "application/octet-stream", "x".getBytes()), "resources");
            assertThat(url).as(name).endsWith(name.substring(name.indexOf('.')));
        }
    }

    @Test
    void executableAndMarkupFilesAreRefusedForTheResourceLibrary() {
        for (String name : new String[]{"page.html", "image.svg", "script.js", "setup.exe", "run.sh", "macro.docm", "noextension"}) {
            MockMultipartFile file = new MockMultipartFile("file", name, "application/pdf", "content".getBytes());
            assertThatThrownBy(() -> fileStorageService.storeResourceFile(file, "resources"))
                    .as(name)
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("isn't supported");
        }
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void anEmptyResourceFileIsRefused() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.storeResourceFile(file, "resources"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No file was uploaded");
    }

    // ---- telling hosted files from external links, reading them back, and deleting them ----

    @Test
    void onlyUrlsUnderThePublicBaseAreConsideredHosted() {
        assertThat(fileStorageService.isHostedUrl("https://cdn.example.com/resources/a.pdf")).isTrue();
        assertThat(fileStorageService.isHostedUrl("https://notion.so/doc")).isFalse();
        assertThat(fileStorageService.isHostedUrl("https://cdn.example.com.evil.com/resources/a.pdf")).as("look-alike host").isFalse();
        assertThat(fileStorageService.isHostedUrl("https://cdn.example.com")).isFalse();
        assertThat(fileStorageService.isHostedUrl(null)).isFalse();
    }

    @Test
    void openingAHostedFileReadsItsKeyFromTheConfiguredBucket() throws Exception {
        GetObjectResponse head = GetObjectResponse.builder().contentType("application/pdf").contentLength(9L).build();
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(head, AbortableInputStream.create(new ByteArrayInputStream("pdf-bytes".getBytes()))));

        FileStorageService.StoredObject object = fileStorageService.open("https://cdn.example.com/resources/a.pdf");

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("resources/a.pdf");
        assertThat(object.contentType()).isEqualTo("application/pdf");
        assertThat(object.contentLength()).isEqualTo(9);
        assertThat(new String(object.stream().readAllBytes())).isEqualTo("pdf-bytes");
    }

    @Test
    void openingAnExternalLinkIsRefused() {
        assertThatThrownBy(() -> fileStorageService.open("https://notion.so/doc")).isInstanceOf(BadRequestException.class);
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void openingAFileThatIsGoneFromStorageIsNotFound() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("gone").build());

        assertThatThrownBy(() -> fileStorageService.open("https://cdn.example.com/resources/gone.pdf"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletingAHostedFileRemovesItsObjectAndAnExternalLinkIsIgnored() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        fileStorageService.deleteIfHosted("https://notion.so/doc");
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));

        fileStorageService.deleteIfHosted("https://cdn.example.com/resources/a.pdf");
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("resources/a.pdf");
    }

    @Test
    void aStorageFailureWhileDeletingIsSwallowedBecauseTheRecordIsAlreadyGone() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().message("boom").build());

        fileStorageService.deleteIfHosted("https://cdn.example.com/resources/a.pdf"); // must not throw
    }
}
