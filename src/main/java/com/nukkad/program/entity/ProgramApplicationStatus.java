package com.nukkad.program.entity;

/**
 * DRAFT is the only status the applicant themselves can still be sitting in while editing — every
 * later status means they've submitted and it's now Admin's to move forward. Terminal statuses are
 * REJECTED and WITHDRAWN, from which the applicant may start a fresh application for the same
 * program (see ProgramApplicationService); SELECTED is also effectively terminal (no path back).
 */
public enum ProgramApplicationStatus {
    DRAFT, SUBMITTED, UNDER_REVIEW, SHORTLISTED, SELECTED, REJECTED, WITHDRAWN
}
