package com.nukkad.matching.dto;

import com.nukkad.opportunity.dto.OpportunityDto;

import java.util.List;

public record OpportunityMatchDto(OpportunityDto opportunity, double score, String matchLabel, List<String> reasons) {
}
