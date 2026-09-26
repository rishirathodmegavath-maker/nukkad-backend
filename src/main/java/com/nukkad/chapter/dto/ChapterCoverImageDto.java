package com.nukkad.chapter.dto;

/** The public URL of an uploaded chapter image, to be sent back as the chapter's {@code coverImageUrl}
 *  or {@code logoUrl} (whichever upload endpoint returned it). */
public record ChapterCoverImageDto(String url) {
}
