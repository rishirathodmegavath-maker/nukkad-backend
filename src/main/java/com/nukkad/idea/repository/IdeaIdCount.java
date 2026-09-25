package com.nukkad.idea.repository;

/** One grouped-count row (interest grouped by idea id) — lets a list endpoint fetch every row's
 *  interest count in one query instead of one query per idea. */
public interface IdeaIdCount {
    String getIdeaId();

    long getTotal();
}
