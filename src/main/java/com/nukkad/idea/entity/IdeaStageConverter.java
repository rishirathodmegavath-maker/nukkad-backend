package com.nukkad.idea.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class IdeaStageConverter extends LabeledEnumConverter<IdeaStage> {
    public IdeaStageConverter() {
        super(IdeaStage.class);
    }
}
