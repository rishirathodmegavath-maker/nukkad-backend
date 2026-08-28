package com.nukkad.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpsertPublicationRequest(@NotBlank @Size(max = 250) String title,
                                        @Size(max = 200) String publisher,
                                        LocalDate publishDate,
                                        String description,
                                        @Size(max = 300) String url) {
}
