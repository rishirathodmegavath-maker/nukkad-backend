package com.nukkad.opportunity.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class OpportunityTypeConverter extends LabeledEnumConverter<OpportunityType> {
    public OpportunityTypeConverter() {
        super(OpportunityType.class);
    }
}
