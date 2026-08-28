package com.nukkad.idea.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum IdeaStage implements LabeledEnum {
    CONCEPT("Concept"),
    VALIDATING("Validating"),
    BUILDING("Building"),
    LAUNCHED("Launched");

    private final String label;

    IdeaStage(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static IdeaStage fromLabel(String label) {
        for (IdeaStage v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown idea stage: " + label);
    }
}
