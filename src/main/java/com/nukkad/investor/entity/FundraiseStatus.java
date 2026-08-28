package com.nukkad.investor.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum FundraiseStatus implements LabeledEnum {
    OPEN("Open"),
    CLOSED("Closed");

    private final String label;

    FundraiseStatus(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static FundraiseStatus fromLabel(String label) {
        for (FundraiseStatus v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown fundraise status: " + label);
    }
}
