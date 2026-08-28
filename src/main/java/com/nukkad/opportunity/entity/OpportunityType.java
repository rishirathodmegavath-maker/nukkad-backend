package com.nukkad.opportunity.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum OpportunityType implements LabeledEnum {
    FULL_TIME("Full-time"),
    INTERNSHIP("Internship"),
    FOUNDING_ROLE("Founding Role"),
    CO_FOUNDER("Co-founder"),
    STARTUP_PROJECT("Startup Project"),
    AI_ML_ROLE("AI/ML Role"),
    CAMPUS("Campus");

    private final String label;

    OpportunityType(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static OpportunityType fromLabel(String label) {
        for (OpportunityType v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown opportunity type: " + label);
    }
}
