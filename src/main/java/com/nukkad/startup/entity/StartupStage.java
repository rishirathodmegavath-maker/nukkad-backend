package com.nukkad.startup.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum StartupStage implements LabeledEnum {
    IDEA("Idea"),
    MVP("MVP"),
    EARLY_TRACTION("Early Traction"),
    GROWTH("Growth"),
    SCALING("Scaling");

    private final String label;

    StartupStage(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static StartupStage fromLabel(String label) {
        for (StartupStage v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown startup stage: " + label);
    }
}
