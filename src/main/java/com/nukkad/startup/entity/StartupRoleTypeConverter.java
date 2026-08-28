package com.nukkad.startup.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class StartupRoleTypeConverter extends LabeledEnumConverter<StartupRoleType> {
    public StartupRoleTypeConverter() {
        super(StartupRoleType.class);
    }
}
