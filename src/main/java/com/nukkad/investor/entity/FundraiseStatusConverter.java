package com.nukkad.investor.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class FundraiseStatusConverter extends LabeledEnumConverter<FundraiseStatus> {
    public FundraiseStatusConverter() {
        super(FundraiseStatus.class);
    }
}
