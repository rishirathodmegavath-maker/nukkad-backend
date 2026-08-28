package com.nukkad.idea.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class ContributionAreaConverter extends LabeledEnumConverter<ContributionArea> {
    public ContributionAreaConverter() {
        super(ContributionArea.class);
    }
}
