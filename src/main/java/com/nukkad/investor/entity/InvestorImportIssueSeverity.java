package com.nukkad.investor.entity;

public enum InvestorImportIssueSeverity {
    /** The row still imported (created or updated) despite this — e.g. an unrecognised investor_type
     *  defaulted to "Other", or a non-numeric investment count was left blank. */
    WARNING,
    /** The row was skipped entirely — e.g. no company name, so no Investor could be created. */
    ERROR
}
