package com.nukkad.opportunity.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class WorkModeConverter extends LabeledEnumConverter<WorkMode> {
    public WorkModeConverter() {
        super(WorkMode.class);
    }
}
