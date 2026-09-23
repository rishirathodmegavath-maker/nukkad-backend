package com.nukkad.common.storage;

import org.apache.poi.poifs.filesystem.FileMagic;

import java.util.Arrays;

/**
 * Verifies an uploaded file's actual leading bytes match the kind its declared MIME type/extension
 * claims. {@code MultipartFile.getContentType()} and the client-sent filename are both attacker-controlled
 * — a renamed executable with a spoofed {@code Content-Type: image/png} header passes every check that
 * trusts either of those alone. This never trusts them alone.
 *
 * <p>Reuses Apache POI's {@link FileMagic} (already a dependency via poi-ooxml, used internally by
 * {@code WorkbookFactory} for the exact same kind of detection) for PNG/JPEG/GIF/PDF/OLE2/OOXML, rather
 * than adding a new content-sniffing library. WEBP, MP4/MOV and WEBM aren't in FileMagic's vocabulary, so
 * those three are small, well-known magic-number checks written out directly.
 */
final class AttachmentContentValidator {

    private AttachmentContentValidator() {}

    /** Enough bytes for every signature below — WEBP and the ISO-base-media (MP4/MOV) check both need to
     * look as far as byte 11. */
    static final int HEADER_BYTES = 16;

    static boolean matches(byte[] header, int length, FileStorageService.AttachmentKind claimedKind) {
        byte[] sample = length == header.length ? header : Arrays.copyOf(header, length);
        FileMagic magic = FileMagic.valueOf(sample);
        return switch (claimedKind) {
            // JPEG is checked directly, not via FileMagic: POI's JPEG pattern only matches a couple of
            // specific marker bytes (0xDB, 0xEE) and misses the two markers the overwhelming majority of
            // real-world JPEGs actually use (0xE0 JFIF, 0xE1 Exif) — confirmed empirically, not assumed.
            case IMAGE -> magic == FileMagic.PNG || magic == FileMagic.GIF || isJpeg(header, length) || isWebp(header, length);
            case VIDEO -> isIsoBaseMedia(header, length) || isWebm(header, length);
            case PDF -> magic == FileMagic.PDF;
            // docx/xlsx/pptx are OOXML (FileMagic recognizes the zip-based container); doc/xls/ppt are OLE2.
            case FILE -> magic == FileMagic.OLE2 || magic == FileMagic.OOXML;
        };
    }

    private static boolean isJpeg(byte[] h, int len) {
        // The 3-byte SOI+marker-start prefix every JPEG variant shares, regardless of which specific
        // marker (JFIF/Exif/etc.) follows.
        return startsWith(h, len, 0xFF, 0xD8, 0xFF);
    }

    private static boolean startsWith(byte[] header, int length, int... expected) {
        if (length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private static boolean isWebp(byte[] h, int len) {
        // "RIFF" (bytes 0-3) alone is shared with WAV/AVI, so bytes 8-11 ("WEBP") must be checked too.
        if (len < 12) return false;
        boolean riff = startsWith(h, len, 0x52, 0x49, 0x46, 0x46);
        boolean webp = (h[8] & 0xFF) == 0x57 && (h[9] & 0xFF) == 0x45 && (h[10] & 0xFF) == 0x42 && (h[11] & 0xFF) == 0x50;
        return riff && webp;
    }

    /** MP4/MOV (ISO base media / QuickTime container): a 4-byte big-endian box size, then a 4-byte box
     * type. A genuine ftyp-first file covers the overwhelming majority of real files; a small set of
     * legacy QuickTime files start with a different first box instead (no ftyp), which the container
     * format itself allows, so those box types are accepted too rather than rejecting real files. */
    private static boolean isIsoBaseMedia(byte[] h, int len) {
        if (len < 8) return false;
        String boxType = new String(h, 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return switch (boxType) {
            case "ftyp", "moov", "mdat", "free", "skip", "wide" -> true;
            default -> false;
        };
    }

    private static boolean isWebm(byte[] h, int len) {
        // The EBML magic number — WebM is a constrained Matroska/EBML profile. A full check would also
        // read the DocType element to confirm "webm" specifically rather than generic Matroska.
        return startsWith(h, len, 0x1A, 0x45, 0xDF, 0xA3);
    }
}
