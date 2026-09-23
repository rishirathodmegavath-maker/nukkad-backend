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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.Duration;

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

    @Mock
    private S3Presigner s3Presigner;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                "test-bucket", "auto", "https://r2.example.com", true, "https://cdn.example.com");
        fileStorageService = new FileStorageService(s3Client, s3Presigner, properties);
    }

    // Real leading bytes for each format storeConversationAttachment's content-sniffing checks against —
    // "content".getBytes() (what every other test in this file still uses, unchanged) would never pass it.
    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] GIF_HEADER = "GIF89a-----".getBytes();
    private static final byte[] WEBP_HEADER = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    private static final byte[] MP4_HEADER = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    private static final byte[] WEBM_HEADER = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] PDF_HEADER = "%PDF-1.4\n\n\n\n".getBytes();
    private static final byte[] OLE2_HEADER = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0, 0, 0};
    private static final byte[] ZIP_HEADER = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] EXE_HEADER = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0};

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

    // ---- feed post attachments: media as before, plus Word / PowerPoint / Excel ----

    private PutObjectRequest storeFeedAttachmentAndCaptureRequest(MockMultipartFile file, FileStorageService.AttachmentKind expected) {
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        FileStorageService.StoredMedia stored = fileStorageService.storeFeedAttachment(file, "feed");
        assertThat(stored.url()).startsWith("https://cdn.example.com/feed/");
        assertThat(stored.kind()).isEqualTo(expected);
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        return captor.getValue();
    }

    @Test
    void aFeedPostMayAttachWordPowerPointAndExcelFilesAsKindFile() {
        String[][] documents = {
                {"proposal.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
                {"proposal.doc", "application/msword"},
                {"deck.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
                {"deck.ppt", "application/vnd.ms-powerpoint"},
                {"model.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
                {"model.xls", "application/vnd.ms-excel"},
        };
        for (String[] doc : documents) {
            org.mockito.Mockito.clearInvocations(s3Client);
            // The client claims a generic type; the stored Content-Type must still come from our table.
            MockMultipartFile file = new MockMultipartFile("file", doc[0], "application/octet-stream", "content".getBytes());

            PutObjectRequest request = storeFeedAttachmentAndCaptureRequest(file, FileStorageService.AttachmentKind.FILE);

            assertThat(request.contentType()).as(doc[0]).isEqualTo(doc[1]);
            assertThat(request.key()).endsWith("." + doc[0].substring(doc[0].lastIndexOf('.') + 1));
        }
    }

    @Test
    void aFeedAttachmentDoesNotTrustAnOfficeFilesClaimedContentType() {
        // Claiming text/html for a .docx must not get the file stored as html.
        MockMultipartFile file = new MockMultipartFile("file", "proposal.docx", "text/html", "content".getBytes());

        PutObjectRequest request = storeFeedAttachmentAndCaptureRequest(file, FileStorageService.AttachmentKind.FILE);

        assertThat(request.contentType()).doesNotContain("html");
    }

    @Test
    void feedAttachmentsStillTakeImagesVideoAndPdfsExactlyAsBefore() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        assertThat(fileStorageService.storeFeedAttachment(
                new MockMultipartFile("file", "a.png", "image/png", "x".getBytes()), "feed").kind())
                .isEqualTo(FileStorageService.AttachmentKind.IMAGE);
        assertThat(fileStorageService.storeFeedAttachment(
                new MockMultipartFile("file", "a.mp4", "video/mp4", "x".getBytes()), "feed").kind())
                .isEqualTo(FileStorageService.AttachmentKind.VIDEO);
        assertThat(fileStorageService.storeFeedAttachment(
                new MockMultipartFile("file", "a.pdf", "application/pdf", "x".getBytes()), "feed").kind())
                .isEqualTo(FileStorageService.AttachmentKind.PDF);
    }

    @Test
    void feedAttachmentsRefuseMarkupScriptsAndArchives() {
        for (String name : new String[]{"page.html", "logo.svg", "run.js", "tool.exe", "bundle.zip", "notes.txt", "noextension"}) {
            MockMultipartFile file = new MockMultipartFile("file", name, "application/octet-stream", "content".getBytes());
            assertThatThrownBy(() -> fileStorageService.storeFeedAttachment(file, "feed"))
                    .as(name).isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Word, PowerPoint or Excel");
        }
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void anEmptyFeedAttachmentIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "deck.pptx", "application/octet-stream", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.storeFeedAttachment(file, "feed"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No file was uploaded");
    }

    @Test
    void startupMaterialsStillRefuseOfficeFilesBecauseStoreMediaIsUnchanged() {
        // StartupService relies on storeMedia returning IMAGE/VIDEO/PDF only; Office files are a feed-only addition.
        MockMultipartFile file = new MockMultipartFile("file", "deck.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", "content".getBytes());

        assertThatThrownBy(() -> fileStorageService.storeMedia(file, "startup-materials"))
                .isInstanceOf(BadRequestException.class);
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

    // ---- chat attachments: private (returns a key, not a URL) and content-validated against real bytes ----

    @Test
    void aConversationAttachmentWithMatchingContentIsStoredAndReturnsAKeyNotAUrl() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", PNG_HEADER);

        FileStorageService.StoredPrivateMedia stored = fileStorageService.storeConversationAttachment(file, "messages");

        assertThat(stored.key()).startsWith("messages/").endsWith(".png");
        assertThat(stored.kind()).isEqualTo(FileStorageService.AttachmentKind.IMAGE);
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(captor.getValue().key()).isEqualTo(stored.key());
    }

    @Test
    void everyAllowedTypeIsAcceptedWhenItsRealBytesMatchItsClaimedType() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        record Case(String name, String contentType, byte[] header, FileStorageService.AttachmentKind kind) {}
        Case[] cases = {
                new Case("a.png", "image/png", PNG_HEADER, FileStorageService.AttachmentKind.IMAGE),
                new Case("a.jpg", "image/jpeg", JPEG_HEADER, FileStorageService.AttachmentKind.IMAGE),
                new Case("a.gif", "image/gif", GIF_HEADER, FileStorageService.AttachmentKind.IMAGE),
                new Case("a.webp", "image/webp", WEBP_HEADER, FileStorageService.AttachmentKind.IMAGE),
                new Case("a.mp4", "video/mp4", MP4_HEADER, FileStorageService.AttachmentKind.VIDEO),
                new Case("a.webm", "video/webm", WEBM_HEADER, FileStorageService.AttachmentKind.VIDEO),
                new Case("a.pdf", "application/pdf", PDF_HEADER, FileStorageService.AttachmentKind.PDF),
                new Case("a.doc", "application/octet-stream", OLE2_HEADER, FileStorageService.AttachmentKind.FILE),
                new Case("a.docx", "application/octet-stream", ZIP_HEADER, FileStorageService.AttachmentKind.FILE),
        };
        for (Case c : cases) {
            MockMultipartFile file = new MockMultipartFile("file", c.name(), c.contentType(), c.header());
            FileStorageService.StoredPrivateMedia stored = fileStorageService.storeConversationAttachment(file, "messages");
            assertThat(stored.kind()).as(c.name()).isEqualTo(c.kind());
        }
    }

    @Test
    void aFileWhoseBytesDontMatchItsClaimedImageTypeIsRejected() {
        // A renamed executable claiming to be a PNG (right Content-Type header and extension, wrong bytes).
        MockMultipartFile file = new MockMultipartFile("file", "totally-a-photo.png", "image/png", EXE_HEADER);

        assertThatThrownBy(() -> fileStorageService.storeConversationAttachment(file, "messages"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("don't match its declared type");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void aFileWhoseBytesDontMatchItsClaimedDocumentTypeIsRejected() {
        // Claims to be a .docx (by extension) but is actually an executable, not a real zip/OOXML container.
        MockMultipartFile file = new MockMultipartFile("file", "resume.docx", "application/octet-stream", EXE_HEADER);

        assertThatThrownBy(() -> fileStorageService.storeConversationAttachment(file, "messages"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("don't match its declared type");
    }

    @Test
    void aFileClaimingAVideoTypeButContainingAPdfIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "movie.mp4", "video/mp4", PDF_HEADER);

        assertThatThrownBy(() -> fileStorageService.storeConversationAttachment(file, "messages"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("don't match its declared type");
    }

    @Test
    void aDisallowedContentTypeForAConversationAttachmentIsStillRejectedBeforeAnyByteSniffing() {
        MockMultipartFile file = new MockMultipartFile("file", "archive.zip", "application/zip", ZIP_HEADER);

        assertThatThrownBy(() -> fileStorageService.storeConversationAttachment(file, "messages"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Word, PowerPoint or Excel");
    }

    @Test
    void anEmptyConversationAttachmentIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.storeConversationAttachment(file, "messages"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No file was uploaded");
    }

    // ---- presigned URLs: the only way to read a private (chat) object back ----

    @Test
    void presignGetBuildsARequestForTheConfiguredBucketAndKeyWithTheGivenTtl() throws Exception {
        PresignedGetObjectRequest presigned = mockPresignedRequest("https://cdn.example.com/messages/abc.png?X-Amz-Signature=xyz");
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String url = fileStorageService.presignGet("messages/abc.png", Duration.ofHours(6));

        assertThat(url).isEqualTo("https://cdn.example.com/messages/abc.png?X-Amz-Signature=xyz");
        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofHours(6));
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("messages/abc.png");
    }

    private static PresignedGetObjectRequest mockPresignedRequest(String url) throws Exception {
        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL(url));
        return presigned;
    }

    // ---- deleting a private object by key (unsend) ----

    @Test
    void deletingByKeyRemovesTheExactObject() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        fileStorageService.deleteByKey("messages/abc.png");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("messages/abc.png");
    }

    @Test
    void deletingByANullKeyIsANoOp() {
        fileStorageService.deleteByKey(null);
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void aStorageFailureWhileDeletingByKeyIsSwallowed() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().message("boom").build());

        fileStorageService.deleteByKey("messages/abc.png"); // must not throw
    }
}
