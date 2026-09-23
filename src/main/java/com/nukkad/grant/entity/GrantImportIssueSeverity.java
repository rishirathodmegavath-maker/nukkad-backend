package com.nukkad.grant.entity;

public enum GrantImportIssueSeverity {
    /** The row still imported despite this — e.g. an unrecognized provider type defaulted to "Other". */
    WARNING,
    /** The row was skipped entirely — e.g. no grant name, so no Grant could be created. */
    ERROR
}
