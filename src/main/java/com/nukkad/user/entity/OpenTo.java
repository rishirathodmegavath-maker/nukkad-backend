package com.nukkad.user.entity;

public enum OpenTo {
    COLLABORATING("Collaborating"),
    BUILDING_IDEAS("Building ideas"),
    STARTUP_PROJECTS("Startup projects"),
    TECHNICAL_PROJECTS("Technical projects"),
    RESEARCH("Research"),
    SPEAKING("Speaking"),
    MENTORSHIP("Mentorship");

    private final String label;

    OpenTo(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
