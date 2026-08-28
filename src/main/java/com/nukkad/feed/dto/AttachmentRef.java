package com.nukkad.feed.dto;

import jakarta.validation.constraints.NotBlank;

/** An already-uploaded, not-yet-attached file — either a create-post request item or an upload response. */
public record AttachmentRef(@NotBlank String url, @NotBlank String kind, String fileName) {
}
