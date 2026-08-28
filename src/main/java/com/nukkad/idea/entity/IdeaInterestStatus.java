package com.nukkad.idea.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum IdeaInterestStatus implements LabeledEnum {
    PENDING("Pending"),
    SHORTLISTED("Shortlisted"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    private final String label;

    IdeaInterestStatus(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static IdeaInterestStatus fromLabel(String label) {
        for (IdeaInterestStatus v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown idea interest status: " + label);
    }

    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED || this == WITHDRAWN;
    }
}
