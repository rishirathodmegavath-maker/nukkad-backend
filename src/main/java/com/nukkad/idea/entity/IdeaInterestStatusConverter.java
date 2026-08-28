package com.nukkad.idea.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class IdeaInterestStatusConverter extends LabeledEnumConverter<IdeaInterestStatus> {
    public IdeaInterestStatusConverter() {
        super(IdeaInterestStatus.class);
    }
}
