package com.nukkad.startup.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum StartupVisibility implements LabeledEnum {
    PUBLIC("Public"),
    NUKKAD_MEMBERS("Nukkad Members");

    private final String label;

    StartupVisibility(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static StartupVisibility fromLabel(String label) {
        for (StartupVisibility v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown startup visibility: " + label);
    }
}
