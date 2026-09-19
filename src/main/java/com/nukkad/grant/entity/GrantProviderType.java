package com.nukkad.grant.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum GrantProviderType implements LabeledEnum {
    GOVERNMENT("Government"),
    ACCELERATOR("Accelerator"),
    CORPORATE("Corporate"),
    FOUNDATION("Foundation"),
    OTHER("Other");

    private final String label;

    GrantProviderType(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static GrantProviderType fromLabel(String label) {
        for (GrantProviderType v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown grant provider type: " + label);
    }
}
