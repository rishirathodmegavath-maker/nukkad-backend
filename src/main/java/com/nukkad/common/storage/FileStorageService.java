package com.nukkad.common.storage;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Uploads validated media to S3-compatible object storage and returns a public, directly-loadable URL. */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    /** FILE is a Word / PowerPoint / Excel document: only {@link #storeFeedAttachment} produces it. */
    public enum AttachmentKind { IMAGE, VIDEO, PDF, FILE }

    public record StoredMedia(String url, AttachmentKind kind) {}

    /** Unlike {@link StoredMedia}, this carries the private object key, not a URL — chat attachments
     * (see {@link #storeConversationAttachment}) are never publicly readable, so there's no URL to hand
     * back at store time; the caller presigns one on demand via {@link #presignGet}. */
    public record StoredPrivateMedia(String key, AttachmentKind kind) {}

    private static final Map<String, AttachmentKind> ALLOWED_MEDIA_CONTENT_TYPES = Map.ofEntries(
            Map.entry("image/png", AttachmentKind.IMAGE),
            Map.entry("image/jpeg", AttachmentKind.IMAGE),
            Map.entry("image/webp", AttachmentKind.IMAGE),
            Map.entry("image/gif", AttachmentKind.IMAGE),
            Map.entry("video/mp4", AttachmentKind.VIDEO),
            Map.entry("video/webm", AttachmentKind.VIDEO),
            Map.entry("video/quicktime", AttachmentKind.VIDEO),
            Map.entry("application/pdf", AttachmentKind.PDF)
    );
    private static final Set<String> ALLOWED_MEDIA_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "gif", "mp4", "webm", "mov", "pdf");

    /**
     * Office documents a member may attach to a feed post, by extension. As with resources the stored Content-Type
     * comes from THIS table, never from what the client claimed.
     */
    private static final Map<String, String> FEED_FILE_CONTENT_TYPES = Map.of(
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls", "application/vnd.ms-excel",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ppt", "application/vnd.ms-powerpoint",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    /**
     * Everything an admin may add to the resource library, keyed by file extension. The stored
     * Content-Type comes from THIS table, never from what the client claimed, so a file can't be
     * labelled as something a browser would treat differently. Deliberately excludes anything a
     * browser would execute or render as markup (html, svg, js, ...): these files are served from a
     * public origin.
     */
    private static final Map<String, String> RESOURCE_CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("csv", "text/csv"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("zip", "application/zip"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("mov", "video/quicktime")
    );

    /** A stored file streamed back out of object storage. The caller must close {@code stream}. */
    public record StoredObject(InputStream stream, String contentType, long contentLength) {}

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public FileStorageService(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    /** Stores an image under {subDir}/ and returns its full public URL. */
    public String storeImage(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Only PNG, JPEG, WEBP or GIF images are allowed");
        }

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String extension = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            extension = contentType.substring(contentType.lastIndexOf('/') + 1);
        }

        return uploadToS3(file, subDir, extension);
    }

    /** Stores an image, video or PDF under {subDir}/ and returns its full public URL plus detected kind. */
    public StoredMedia storeMedia(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        String contentType = file.getContentType();
        AttachmentKind kind = contentType == null ? null : ALLOWED_MEDIA_CONTENT_TYPES.get(contentType.toLowerCase());
        if (kind == null) {
            throw new BadRequestException("Only images, videos (mp4/webm/mov) or PDFs are allowed");
        }

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String extension = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_MEDIA_EXTENSIONS.contains(extension)) {
            extension = contentType.substring(contentType.lastIndexOf('/') + 1);
        }

        String url = uploadToS3(file, subDir, extension);
        return new StoredMedia(url, kind);
    }

    /**
     * A feed post's attachment: everything {@link #storeMedia} takes (images, video, PDF) plus Word, PowerPoint and
     * Excel documents (kind {@link AttachmentKind#FILE}). Separate from storeMedia on purpose: startup materials also
     * use storeMedia and expect PDFs only.
     */
    public StoredMedia storeFeedAttachment(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        String contentType = file.getContentType();
        if (contentType != null && ALLOWED_MEDIA_CONTENT_TYPES.containsKey(contentType.toLowerCase())) {
            return storeMedia(file, subDir);
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String extension = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        String documentType = FEED_FILE_CONTENT_TYPES.get(extension);
        if (documentType == null) {
            throw new BadRequestException("Attach an image, a video (mp4/webm/mov), a PDF, or a Word, PowerPoint or Excel file");
        }
        return new StoredMedia(uploadToS3(file, subDir, extension, documentType), AttachmentKind.FILE);
    }

    /**
     * A chat attachment: same allowed types as {@link #storeFeedAttachment} (images, video, PDF, Word/
     * PowerPoint/Excel documents) and the same global size ceiling — but two things are different from
     * every other {@code store*} method here. First, the declared content-type/extension is cross-checked
     * against the file's actual leading bytes ({@link AttachmentContentValidator}) before it's stored, since
     * a chat attachment is more attractive to mislabel than a public feed image. Second, this returns the
     * object KEY, not a URL: chat attachments are private (see {@link #presignGet}), unlike everything else
     * this service writes, which is why this is a separate method rather than a flag on storeFeedAttachment.
     */
    public StoredPrivateMedia storeConversationAttachment(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        AttachmentKind kind;
        String extension;
        String contentType = file.getContentType();
        if (contentType != null && ALLOWED_MEDIA_CONTENT_TYPES.containsKey(contentType.toLowerCase())) {
            kind = ALLOWED_MEDIA_CONTENT_TYPES.get(contentType.toLowerCase());
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            extension = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
            if (!ALLOWED_MEDIA_EXTENSIONS.contains(extension)) {
                extension = contentType.substring(contentType.lastIndexOf('/') + 1);
            }
        } else {
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            extension = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
            String documentType = FEED_FILE_CONTENT_TYPES.get(extension);
            if (documentType == null) {
                throw new BadRequestException("Attach an image, a video (mp4/webm/mov), a PDF, or a Word, PowerPoint or Excel file");
            }
            kind = AttachmentKind.FILE;
            contentType = documentType;
        }

        byte[] header = readHeader(file);
        if (!AttachmentContentValidator.matches(header, header.length, kind)) {
            throw new BadRequestException("This file's contents don't match its declared type — try re-exporting or re-saving it");
        }

        String key = subDir + "/" + UUID.randomUUID() + "." + extension;
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }
        return new StoredPrivateMedia(key, kind);
    }

    /** The first {@link AttachmentContentValidator#HEADER_BYTES} bytes of the upload, for content-sniffing —
     * a fresh {@link MultipartFile#getInputStream()} call, entirely separate from the one used to actually
     * store the file below, since Spring's multipart file already has its bytes fully received/buffered
     * before this method runs (each call opens its own stream over the same underlying data). */
    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] buffer = new byte[AttachmentContentValidator.HEADER_BYTES];
            int read = in.readNBytes(buffer, 0, buffer.length);
            return read == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    /** A short-lived, temporary-credential URL for a private object (see {@link #storeConversationAttachment}) —
     * the only way to read one back, since it's never given a permanent public URL. Presigning is a local
     * signature computation (no network round-trip to storage), so calling this once per attachment on every
     * message read (REST page or WebSocket broadcast) is cheap. */
    public String presignGet(String key, Duration ttl) {
        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(properties.bucket()).key(key).build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /** Deletes a private object by its key directly (no URL to parse, unlike {@link #deleteIfHosted}).
     * Best-effort, like deleteIfHosted: a storage hiccup is logged, not thrown, since the caller (unsend)
     * has already committed to the message being gone and must not resurrect it over a delete failure. */
    public void deleteByKey(String key) {
        if (key == null) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (RuntimeException e) {
            log.warn("Could not delete stored file {}: {}", key, e.getMessage());
        }
    }

    /**
     * Stores a file for the admin-curated resource library and returns its public URL. Unlike
     * {@link #storeMedia} this accepts real documents (PDF, Word, Excel, PowerPoint, CSV, text, ZIP) as
     * well as images and videos. The file type is decided by the extension against a fixed allow-list,
     * and the stored Content-Type is ours, not the client's (see {@link #RESOURCE_CONTENT_TYPES}).
     */
    public String storeResourceFile(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String extension = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        String contentType = RESOURCE_CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw new BadRequestException(
                    "This file type isn't supported. Upload a PDF, Word, Excel, PowerPoint, CSV, text or ZIP file, "
                            + "an image (png/jpg/webp/gif) or a video (mp4/webm/mov).");
        }
        return uploadToS3(file, subDir, extension, contentType);
    }

    /** True when {@code url} points at a file this service stored (as opposed to an external link). */
    public boolean isHostedUrl(String url) {
        return url != null && url.startsWith(properties.publicBaseUrl() + "/");
    }

    /** Streams back a file previously stored by this service, identified by the public URL it returned. */
    public StoredObject open(String url) {
        if (!isHostedUrl(url)) {
            throw new BadRequestException("This resource is a link, not a file hosted on BuildAdda");
        }
        String key = url.substring(properties.publicBaseUrl().length() + 1);
        try {
            ResponseInputStream<GetObjectResponse> object = s3Client.getObject(
                    GetObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            GetObjectResponse head = object.response();
            return new StoredObject(object, head.contentType(), head.contentLength() == null ? -1 : head.contentLength());
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException("The file for this resource is no longer available");
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to read stored file", e);
        }
    }

    /**
     * Removes a file this service stored, so a deleted resource stops being reachable at its public
     * URL. Best effort: an external link is ignored, and a storage failure is logged rather than
     * thrown, because the database record is already gone and must not be resurrected by a hiccup here.
     */
    public void deleteIfHosted(String url) {
        if (!isHostedUrl(url)) return;
        String key = url.substring(properties.publicBaseUrl().length() + 1);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (RuntimeException e) {
            log.warn("Could not delete stored file {}: {}", key, e.getMessage());
        }
    }

    private String uploadToS3(MultipartFile file, String subDir, String extension) {
        return uploadToS3(file, subDir, extension, file.getContentType());
    }

    private String uploadToS3(MultipartFile file, String subDir, String extension, String contentType) {
        String key = subDir + "/" + UUID.randomUUID() + "." + extension;
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }
        return properties.publicBaseUrl() + "/" + key;
    }
}
