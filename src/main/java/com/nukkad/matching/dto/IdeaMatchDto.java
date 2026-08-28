package com.nukkad.matching.dto;

import com.nukkad.idea.dto.IdeaDto;

import java.util.List;

public record IdeaMatchDto(IdeaDto idea, double score, String matchLabel, List<String> reasons) {
}
