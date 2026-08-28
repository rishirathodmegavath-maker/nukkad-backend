package com.nukkad.startup.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class StartupStageConverter extends LabeledEnumConverter<StartupStage> {
    public StartupStageConverter() {
        super(StartupStage.class);
    }
}
