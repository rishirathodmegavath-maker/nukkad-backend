package com.nukkad.idea.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum ContributionArea implements LabeledEnum {
    AI_ML("AI/ML"),
    TECHNOLOGY("Technology"),
    PRODUCT("Product"),
    DESIGN("Design"),
    MARKETING("Marketing"),
    SALES("Sales"),
    OPERATIONS("Operations"),
    DOMAIN_EXPERTISE("Domain Expertise");

    private final String label;

    ContributionArea(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static ContributionArea fromLabel(String label) {
        for (ContributionArea v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown contribution area: " + label);
    }
}
