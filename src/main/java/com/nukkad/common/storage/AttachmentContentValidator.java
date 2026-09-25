package com.nukkad.common.storage;

import org.apache.poi.poifs.filesystem.FileMagic;

import java.nio.charset.StandardCharsets;
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
 *
 * <p>Where a bare magic number is too easy to satisfy on purpose (the first four bytes of an HTML file can
 * be made to read as a valid MP4 box size; a ZIP of anything at all starts with the same bytes as a .docx),
 * the check goes one structural step further — a plausible first-box size, a real WebM DocType, a real OOXML
 * package part — so a file that merely starts with the right bytes doesn't pass. This is content-type
 * validation, not antivirus: it can't say a well-formed document is free of macros or exploits.
 */
final class AttachmentContentValidator {

    private AttachmentContentValidator() {}

    /** Enough bytes for every signature below — the WEBM DocType and the first ZIP entry name both sit
     * a few dozen bytes in, well past what the fixed-offset checks (WEBP, MP4/MOV) need. */
    static final int HEADER_BYTES = 64;

    /** {@code fileSize} is the whole upload's real size (not the header's length) — it bounds how large
     * the first MP4/MOV box may plausibly declare itself to be. */
    static boolean matches(byte[] header, int length, long fileSize, FileStorageService.AttachmentKind claimedKind) {
        byte[] sample = length == header.length ? header : Arrays.copyOf(header, length);
        FileMagic magic = FileMagic.valueOf(sample);
        return switch (claimedKind) {
            // JPEG is checked directly, not via FileMagic: POI's JPEG pattern only matches a couple of
            // specific marker bytes (0xDB, 0xEE) and misses the two markers the overwhelming majority of
            // real-world JPEGs actually use (0xE0 JFIF, 0xE1 Exif) — confirmed empirically, not assumed.
            case IMAGE -> magic == FileMagic.PNG || magic == FileMagic.GIF || isJpeg(header, length) || isWebp(header, length);
            case VIDEO -> isIsoBaseMedia(header, length, fileSize) || isWebm(header, length);
            case PDF -> magic == FileMagic.PDF;
            // docx/xlsx/pptx are OOXML; doc/xls/ppt are OLE2. FileMagic.OOXML alone is just the ZIP
            // signature — any zip, jar or apk starts with it — so the package's first part is checked too.
            case FILE -> magic == FileMagic.OLE2 || (magic == FileMagic.OOXML && isOoxmlPackage(header, length));
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
     * format itself allows, so those box types are accepted too rather than rejecting real files.
     *
     * <p>The box type alone is four bytes anyone can write into a text file, so the declared size must
     * also be plausible: 0 (box runs to end of file) or 1 (64-bit size follows) as the format allows, or
     * else at least the 8-byte box header and no larger than the file itself. A file that begins with
     * ordinary text ("&lt;!--", newlines, ...) reads as a size of hundreds of MB or more and fails that. An
     * {@code ftyp} box is additionally tiny in every real file (a major brand plus a short brand list) and
     * its brand is four printable ASCII characters. */
    private static boolean isIsoBaseMedia(byte[] h, int len, long fileSize) {
        if (len < 12) return false;
        long boxSize = ((h[0] & 0xFFL) << 24) | ((h[1] & 0xFFL) << 16) | ((h[2] & 0xFFL) << 8) | (h[3] & 0xFFL);
        boolean plausibleSize = boxSize == 0 || boxSize == 1 || (boxSize >= 8 && boxSize <= fileSize);
        if (!plausibleSize) return false;
        String boxType = new String(h, 4, 4, StandardCharsets.US_ASCII);
        return switch (boxType) {
            case "ftyp" -> boxSize >= 12 && boxSize <= 4096 && isPrintableAscii(h, 8, 4);
            case "moov", "mdat", "free", "skip", "wide" -> true;
            default -> false;
        };
    }

    private static boolean isPrintableAscii(byte[] h, int offset, int count) {
        for (int i = offset; i < offset + count; i++) {
            int b = h[i] & 0xFF;
            if (b < 0x20 || b > 0x7E) return false;
        }
        return true;
    }

    /** WebM is a constrained Matroska/EBML profile: the EBML magic number, then a header that carries a
     * DocType of "webm". Generic Matroska (DocType "matroska") shares the magic number but isn't a WebM
     * upload, so the DocType text is required too — it sits well inside the first {@link #HEADER_BYTES}. */
    private static boolean isWebm(byte[] h, int len) {
        if (!startsWith(h, len, 0x1A, 0x45, 0xDF, 0xA3)) return false;
        for (int i = 4; i + 4 <= len; i++) {
            if (h[i] == 'w' && h[i + 1] == 'e' && h[i + 2] == 'b' && h[i + 3] == 'm') return true;
        }
        return false;
    }

    /** The first entry of the ZIP must be one of the parts an Office Open XML package is made of. Every
     * real .docx/.xlsx/.pptx opens with {@code [Content_Types].xml} (or, from a few generators, another
     * standard package part); an arbitrary zip, a jar ({@code META-INF/...}) or an apk does not. Reads the
     * first local-file-header's name straight out of the header window: signature(4) ... nameLength(2) at
     * offset 26, extraLength(2) at 28, name from 30. */
    private static boolean isOoxmlPackage(byte[] h, int len) {
        if (len < 30) return false;
        int nameLength = (h[26] & 0xFF) | ((h[27] & 0xFF) << 8);
        if (nameLength == 0) return false;
        int visible = Math.min(nameLength, len - 30);
        boolean complete = visible == nameLength;
        String name = new String(h, 30, visible, StandardCharsets.UTF_8);
        if (complete && (name.equals("[Content_Types].xml") || name.equals("_rels/.rels"))) return true;
        return name.startsWith("docProps/") || name.startsWith("word/") || name.startsWith("xl/")
                || name.startsWith("ppt/") || name.startsWith("customXml/");
    }
}
