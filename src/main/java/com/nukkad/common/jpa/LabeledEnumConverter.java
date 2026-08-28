package com.nukkad.common.jpa;

import jakarta.persistence.AttributeConverter;

/**
 * Base for converters that persist an enum's human-readable label (matching the frontend's
 * string union and the DB's SQL ENUM literal) instead of Enum.name(), for constants whose
 * label contains characters (spaces, slashes) that aren't valid in a Java identifier.
 */
public abstract class LabeledEnumConverter<E extends Enum<E> & LabeledEnum> implements AttributeConverter<E, String> {

    private final Class<E> enumClass;

    protected LabeledEnumConverter(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        for (E constant : enumClass.getEnumConstants()) {
            if (constant.getLabel().equals(dbData)) return constant;
        }
        throw new IllegalArgumentException("Unknown value for " + enumClass.getSimpleName() + ": " + dbData);
    }
}
