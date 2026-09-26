package com.nukkad.resource.dto;

import java.time.Instant;
import java.util.Set;

public record ResourceDto(
        String id,
        String title,
        String description,
        String type,
        /** Category slug ("free-learning"), or null if the resource isn't filed under one. */
        String category,
        String provider,
        /** A hosted image for cards, or null (the client then derives one from the link or draws a placeholder). */
        String thumbnailUrl,
        Integer durationMinutes,
        boolean featured,
        String url,
        String uploaderUserId,
        String publisherIdentity,
        String chapterId,
        String chapterName,
        Set<String> tags,
        boolean isSaved,
        /** Non-null only for a file hosted on BuildAdda: the name a download is saved as. Null for a link. */
        String fileName,
        /** True when the hosted file can be shown in the browser (PDF, image, video, plain text) rather than
         *  only downloaded. Always false for links and for Office/ZIP/CSV files. */
        boolean previewable,
        Instant createdAt,
        Instant updatedAt
) {
}
