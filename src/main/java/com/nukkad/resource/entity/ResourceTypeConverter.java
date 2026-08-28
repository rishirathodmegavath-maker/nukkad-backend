package com.nukkad.resource.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class ResourceTypeConverter extends LabeledEnumConverter<ResourceType> {
    public ResourceTypeConverter() {
        super(ResourceType.class);
    }
}
