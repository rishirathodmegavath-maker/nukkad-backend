package com.nukkad.opportunity.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum ApplicationStatus implements LabeledEnum {
    PENDING("Pending"),
    SHORTLISTED("Shortlisted"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static ApplicationStatus fromLabel(String label) {
        for (ApplicationStatus v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown application status: " + label);
    }

    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED || this == WITHDRAWN;
    }
}
