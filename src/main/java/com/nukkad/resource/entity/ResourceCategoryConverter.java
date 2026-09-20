package com.nukkad.resource.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ResourceCategoryConverter implements AttributeConverter<ResourceCategory, String> {

    @Override
    public String convertToDatabaseColumn(ResourceCategory attribute) {
        return attribute == null ? null : attribute.getSlug();
    }

    @Override
    public ResourceCategory convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ResourceCategory.fromSlug(dbData);
    }
}
