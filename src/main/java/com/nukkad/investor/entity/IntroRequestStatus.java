package com.nukkad.investor.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum IntroRequestStatus implements LabeledEnum {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    private final String label;

    IntroRequestStatus(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static IntroRequestStatus fromLabel(String label) {
        for (IntroRequestStatus v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown intro request status: " + label);
    }

    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED || this == WITHDRAWN;
    }
}
