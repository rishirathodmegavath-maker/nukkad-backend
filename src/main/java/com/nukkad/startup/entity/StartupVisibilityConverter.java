package com.nukkad.startup.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class StartupVisibilityConverter extends LabeledEnumConverter<StartupVisibility> {
    public StartupVisibilityConverter() {
        super(StartupVisibility.class);
    }
}
