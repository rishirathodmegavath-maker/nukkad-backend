package com.nukkad.common.storage;

import com.nukkad.common.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Uploads validated media to S3-compatible object storage and returns a public, directly-loadable URL. */
@Service
public class FileStorageService {

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

    private String uploadToS3(MultipartFile file, String subDir, String extension) {
        String key = subDir + "/" + UUID.randomUUID() + "." + extension;
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }
        return properties.publicBaseUrl() + "/" + key;
    }
}
