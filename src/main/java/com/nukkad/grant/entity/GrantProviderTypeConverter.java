package com.nukkad.grant.entity;

import com.nukkad.common.jpa.LabeledEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class GrantProviderTypeConverter extends LabeledEnumConverter<GrantProviderType> {
    public GrantProviderTypeConverter() {
        super(GrantProviderType.class);
    }
}
