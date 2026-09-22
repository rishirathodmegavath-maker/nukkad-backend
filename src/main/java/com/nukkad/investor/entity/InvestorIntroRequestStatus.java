package com.nukkad.investor.entity;

/** Lifecycle of a "recorded" introduction request — see {@link InvestorIntroRequest}. There's no
 *  accept/decline here (there's no live account to decide): PENDING means an admin hasn't actioned it yet,
 *  CLOSED means one has (however they followed up), which frees the founder to ask again later. */
public enum InvestorIntroRequestStatus {
    PENDING, CLOSED
}
