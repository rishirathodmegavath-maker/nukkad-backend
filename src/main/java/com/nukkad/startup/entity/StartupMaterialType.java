package com.nukkad.startup.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum StartupMaterialType implements LabeledEnum {
    WEBSITE("Website"),
    PITCH_DECK("Pitch Deck"),
    PRODUCT_DEMO("Product Demo"),
    SCREENSHOTS("Screenshots"),
    LINKEDIN("LinkedIn"),
    X("X"),
    OTHER_DOCUMENT("Other Document");

    private final String label;

    StartupMaterialType(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    /** Website/LinkedIn/X are external links the founder supplies; everything else is an upload
     *  through the existing S3-backed FileStorageService. */
    public boolean isExternalLink() {
        return this == WEBSITE || this == LINKEDIN || this == X;
    }

    public static StartupMaterialType fromLabel(String label) {
        for (StartupMaterialType v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown material type: " + label);
    }
}
