package com.nukkad.investor.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class IntroRequestStatusConverter extends LabeledEnumConverter<IntroRequestStatus> {
    public IntroRequestStatusConverter() {
        super(IntroRequestStatus.class);
    }
}
