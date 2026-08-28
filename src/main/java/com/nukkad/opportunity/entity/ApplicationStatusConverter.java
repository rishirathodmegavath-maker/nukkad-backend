package com.nukkad.opportunity.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class ApplicationStatusConverter extends LabeledEnumConverter<ApplicationStatus> {
    public ApplicationStatusConverter() {
        super(ApplicationStatus.class);
    }
}
