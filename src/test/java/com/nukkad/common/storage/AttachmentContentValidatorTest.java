package com.nukkad.common.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentContentValidatorTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] GIF = "GIF89a-----".getBytes();
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    private static final byte[] RIFF_BUT_NOT_WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] PDF = "%PDF-1.4\n\n\n\n".getBytes();
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0, 0, 0};
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0};
    private static final byte[] EMPTY = {};

    private boolean matches(byte[] header, FileStorageService.AttachmentKind kind) {
        return AttachmentContentValidator.matches(header, header.length, kind);
    }

    @Test
    void recognizesEveryAllowedImageFormat() {
        assertThat(matches(PNG, FileStorageService.AttachmentKind.IMAGE)).isTrue();
        assertThat(matches(JPEG, FileStorageService.AttachmentKind.IMAGE)).isTrue();
        assertThat(matches(GIF, FileStorageService.AttachmentKind.IMAGE)).isTrue();
        assertThat(matches(WEBP, FileStorageService.AttachmentKind.IMAGE)).isTrue();
    }

    @Test
    void webpRequiresBothTheRiffAndWebpMarkersNotJustRiff() {
        // RIFF alone is shared with WAV/AVI — bytes 8-11 must actually say "WEBP".
        assertThat(matches(RIFF_BUT_NOT_WEBP, FileStorageService.AttachmentKind.IMAGE)).isFalse();
    }

    @Test
    void recognizesEveryAllowedVideoFormat() {
        assertThat(matches(MP4, FileStorageService.AttachmentKind.VIDEO)).isTrue();
        assertThat(matches(WEBM, FileStorageService.AttachmentKind.VIDEO)).isTrue();
    }

    @Test
    void recognizesPdf() {
        assertThat(matches(PDF, FileStorageService.AttachmentKind.PDF)).isTrue();
    }

    @Test
    void recognizesBothLegacyOle2AndModernOoxmlOfficeDocuments() {
        assertThat(matches(OLE2, FileStorageService.AttachmentKind.FILE)).isTrue();
        assertThat(matches(ZIP, FileStorageService.AttachmentKind.FILE)).isTrue();
    }

    @Test
    void anExecutableIsNeverAcceptedAsAnyAllowedKindRegardlessOfWhatItClaimsToBe() {
        assertThat(matches(EXE, FileStorageService.AttachmentKind.IMAGE)).isFalse();
        assertThat(matches(EXE, FileStorageService.AttachmentKind.VIDEO)).isFalse();
        assertThat(matches(EXE, FileStorageService.AttachmentKind.PDF)).isFalse();
        assertThat(matches(EXE, FileStorageService.AttachmentKind.FILE)).isFalse();
    }

    @Test
    void bytesForOneKindDoNotMatchAnotherClaimedKind() {
        // A real PDF renamed/relabeled to claim it's an image, video or Office document.
        assertThat(matches(PDF, FileStorageService.AttachmentKind.IMAGE)).isFalse();
        assertThat(matches(PDF, FileStorageService.AttachmentKind.VIDEO)).isFalse();
        assertThat(matches(PDF, FileStorageService.AttachmentKind.FILE)).isFalse();
    }

    @Test
    void anEmptyOrTooShortHeaderNeverMatchesAnything() {
        assertThat(matches(EMPTY, FileStorageService.AttachmentKind.IMAGE)).isFalse();
        assertThat(matches(EMPTY, FileStorageService.AttachmentKind.VIDEO)).isFalse();
        assertThat(matches(EMPTY, FileStorageService.AttachmentKind.PDF)).isFalse();
        assertThat(matches(EMPTY, FileStorageService.AttachmentKind.FILE)).isFalse();
    }
}
