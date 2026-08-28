package com.nukkad.common.storage;

import com.nukkad.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    public enum AttachmentKind { IMAGE, VIDEO, PDF }

    public record StoredMedia(String path, AttachmentKind kind) {}

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

    private final Path uploadsRoot;

    public FileStorageService(@Value("${nukkad.uploads.dir}") String uploadsDir) {
        this.uploadsRoot = Path.of(uploadsDir).toAbsolutePath().normalize();
    }

    /** Stores an image under uploads/{subDir}/ and returns its path relative to the uploads root (e.g. "avatars/abc.png"). */
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

        return writeToDisk(file, subDir, extension);
    }

    /** Stores an image, video or PDF under uploads/{subDir}/ and returns its relative path plus detected kind. */
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

        String path = writeToDisk(file, subDir, extension);
        return new StoredMedia(path, kind);
    }

    private String writeToDisk(MultipartFile file, String subDir, String extension) {
        String filename = UUID.randomUUID() + "." + extension;
        try {
            Path targetDir = uploadsRoot.resolve(subDir).normalize();
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(filename).normalize();
            if (!target.startsWith(targetDir)) {
                throw new BadRequestException("Invalid filename");
            }
            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }
        return subDir + "/" + filename;
    }
}
