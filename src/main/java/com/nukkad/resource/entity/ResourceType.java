package com.nukkad.resource.entity;

import com.nukkad.common.jpa.LabeledEnum;

public enum ResourceType implements LabeledEnum {
    DOCUMENT("Document"),
    LINK("Link"),
    VIDEO("Video"),
    NOTE("Note"),
    TEMPLATE("Template");

    private final String label;

    ResourceType(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static ResourceType fromLabel(String label) {
        for (ResourceType v : values()) {
            if (v.label.equalsIgnoreCase(label)) return v;
        }
        throw new IllegalArgumentException("Unknown resource type: " + label);
    }
}
