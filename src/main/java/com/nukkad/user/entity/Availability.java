package com.nukkad.user.entity;

public enum Availability {
    FULL_TIME("Full-time"),
    PART_TIME("Part-time"),
    WEEKENDS("Weekends"),
    NOT_AVAILABLE("Not available");

    private final String label;

    Availability(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Availability fromLabel(String label) {
        for (Availability a : values()) {
            if (a.label.equals(label)) return a;
        }
        throw new IllegalArgumentException("Unknown availability value: " + label);
    }
}
