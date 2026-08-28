package com.nukkad.investor.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum InvestorType implements LabeledEnum {
    ANGEL("Angel"),
    VC("VC"),
    FAMILY_OFFICE("Family Office"),
    CORPORATE_VC("Corporate VC"),
    ACCELERATOR("Accelerator"),
    OTHER("Other");

    private final String label;

    InvestorType(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static InvestorType fromLabel(String label) {
        for (InvestorType v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown investor type: " + label);
    }
}
