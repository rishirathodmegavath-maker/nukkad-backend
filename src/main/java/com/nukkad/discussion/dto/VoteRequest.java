package com.nukkad.discussion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VoteRequest(@NotBlank @Pattern(regexp = "up|down") String direction) {
}
