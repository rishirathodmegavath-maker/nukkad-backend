package com.nukkad.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

@Converter
public class OpenToConverter implements AttributeConverter<OpenTo, String> {

    @Override
    public String convertToDatabaseColumn(OpenTo attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public OpenTo convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return Arrays.stream(OpenTo.values())
                .filter(v -> v.getLabel().equals(dbData))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown open_to value: " + dbData));
    }
}
