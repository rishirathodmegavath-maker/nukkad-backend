package com.nukkad.auth.entity;

/** Which sign-in surface a password-reset token was issued for. The two are never interchangeable:
 *  an ADMIN token only works through the admin reset flow, a MEMBER token only through the member one. */
public enum ResetAudience {
    MEMBER,
    ADMIN
}
