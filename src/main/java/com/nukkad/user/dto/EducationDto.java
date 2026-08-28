package com.nukkad.user.dto;

public record EducationDto(String id, String institution, String degree, String fieldOfStudy,
                            Integer startYear, Integer endYear, String grade, String description, int sortOrder) {
}
