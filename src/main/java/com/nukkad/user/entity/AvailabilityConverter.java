package com.nukkad.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AvailabilityConverter implements AttributeConverter<Availability, String> {

    @Override
    public String convertToDatabaseColumn(Availability attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public Availability convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Availability.fromLabel(dbData);
    }
}
