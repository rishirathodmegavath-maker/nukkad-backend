package com.nukkad.common.storage;

import com.nukkad.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
