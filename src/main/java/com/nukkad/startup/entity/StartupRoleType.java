package com.nukkad.startup.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum StartupRoleType implements LabeledEnum {
    JOB("Job"),
    INTERNSHIP("Internship"),
    FOUNDING_ROLE("Founding Role");

    private final String label;

    StartupRoleType(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static StartupRoleType fromLabel(String label) {
        for (StartupRoleType v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown startup role type: " + label);
    }
}
