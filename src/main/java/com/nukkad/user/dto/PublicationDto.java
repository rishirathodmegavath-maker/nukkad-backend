package com.nukkad.user.dto;

import java.time.LocalDate;

public record PublicationDto(String id, String title, String publisher, LocalDate publishDate,
                              String description, String url, int sortOrder) {
}
