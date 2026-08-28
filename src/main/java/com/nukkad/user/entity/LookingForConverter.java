package com.nukkad.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

@Converter
public class LookingForConverter implements AttributeConverter<LookingFor, String> {

    @Override
    public String convertToDatabaseColumn(LookingFor attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public LookingFor convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return Arrays.stream(LookingFor.values())
                .filter(v -> v.getLabel().equals(dbData))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown looking_for value: " + dbData));
    }
}
