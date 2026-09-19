package com.nukkad.opportunity.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum WorkMode implements LabeledEnum {
    REMOTE("Remote"),
    HYBRID("Hybrid"),
    IN_PERSON("In-person");

    private final String label;

    WorkMode(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static WorkMode fromLabel(String label) {
        for (WorkMode v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown work mode: " + label);
    }
}
