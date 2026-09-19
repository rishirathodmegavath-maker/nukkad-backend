package com.nukkad.user.entity;

/** Platform-level account standing, set only by an Admin — distinct from {@link SecurityRole},
 *  which grants capabilities. SUSPENDED/DISABLED accounts are rejected at login and refresh. */
public enum AccountStatus {
    ACTIVE, SUSPENDED, DISABLED
}
