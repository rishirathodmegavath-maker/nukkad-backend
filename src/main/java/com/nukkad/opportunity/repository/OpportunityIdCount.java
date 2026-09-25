package com.nukkad.opportunity.repository;

/** One grouped-count row (e.g. applicants or interest, grouped by opportunity id) — lets a list
 *  endpoint fetch every row's count in one query instead of one query per opportunity. */
public interface OpportunityIdCount {
    String getOpportunityId();

    long getTotal();
}
