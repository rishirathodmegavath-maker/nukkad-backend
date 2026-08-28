package com.nukkad.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertEducationRequest(@NotBlank @Size(max = 200) String institution,
                                      @Size(max = 150) String degree,
                                      @Size(max = 150) String fieldOfStudy,
                                      Integer startYear,
                                      Integer endYear,
                                      @Size(max = 50) String grade,
                                      String description) {
}
