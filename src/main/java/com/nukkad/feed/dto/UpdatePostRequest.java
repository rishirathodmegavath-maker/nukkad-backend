package com.nukkad.feed.dto;

import jakarta.validation.constraints.Size;

public record UpdatePostRequest(@Size(max = 4000) String content) {
}
