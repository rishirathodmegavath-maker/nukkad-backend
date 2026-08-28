package com.nukkad.investor.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class InvestorTypeConverter extends LabeledEnumConverter<InvestorType> {
    public InvestorTypeConverter() {
        super(InvestorType.class);
    }
}
