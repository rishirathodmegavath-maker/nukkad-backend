package com.nukkad.user.entity;

public enum LookingFor {
    CO_FOUNDER("Co-founder"),
    TEAM_TO_JOIN("Team to join"),
    MENTORSHIP("Mentorship"),
    INVESTMENT("Investment"),
    JOB("Job"),
    INTERNSHIP("Internship"),
    FOUNDING_ROLE("Founding Role"),
    COLLABORATORS("Collaborators");

    private final String label;

    LookingFor(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
