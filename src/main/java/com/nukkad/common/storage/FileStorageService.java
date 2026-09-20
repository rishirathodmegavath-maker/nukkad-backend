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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Uploads validated media to S3-compatible object storage and returns a public, directly-loadable URL. */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    public enum AttachmentKind { IMAGE, VIDEO, PDF }

    public record StoredMedia(String url, AttachmentKind kind) {}

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
    private final StorageProperties properties;

    public FileStorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
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
