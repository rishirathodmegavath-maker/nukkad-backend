package com.nukkad.startup.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class StartupMaterialTypeConverter extends LabeledEnumConverter<StartupMaterialType> {
    public StartupMaterialTypeConverter() {
        super(StartupMaterialType.class);
    }
}
