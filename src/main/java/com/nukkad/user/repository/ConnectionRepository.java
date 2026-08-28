package com.nukkad.user.repository;

import com.nukkad.user.entity.Connection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConnectionRepository extends JpaRepository<Connection, String> {
    Optional<Connection> findByUserAIdAndUserBId(String userAId, String userBId);
    boolean existsByUserAIdAndUserBId(String userAId, String userBId);
    long countByUserAIdOrUserBId(String userAId, String userBId);

    @Query("select c from Connection c where (c.userAId = :viewerId and c.userBId in :otherIds) "
            + "or (c.userBId = :viewerId and c.userAId in :otherIds)")
    List<Connection> findAllInvolvingViewer(@Param("viewerId") String viewerId, @Param("otherIds") Collection<String> otherIds);

    @Query("select c from Connection c where (c.userAId = :userId or c.userBId = :userId) and c.status = com.nukkad.user.entity.Connection.Status.ACCEPTED")
    List<Connection> findAcceptedConnections(@Param("userId") String userId);

    /** Every relationship row touching {@code userId} regardless of status — used to exclude people who
     *  are already connected or have a pending request either way from "people you may know" candidates. */
    @Query("select c from Connection c where c.userAId = :userId or c.userBId = :userId")
    List<Connection> findAllInvolving(@Param("userId") String userId);

    @Query("select case when count(c) > 0 then true else false end from Connection c "
            + "where ((c.userAId = :a and c.userBId = :b) or (c.userAId = :b and c.userBId = :a)) "
            + "and c.status = com.nukkad.user.entity.Connection.Status.ACCEPTED")
    boolean existsAcceptedBetween(@Param("a") String a, @Param("b") String b);

    /**
     * Every accepted edge touching any of {@code ids} — the entire 2nd-hop expansion of a BFS frontier
     * in one query, regardless of how many neighbors each frontier member has. Used instead of one
     * per-neighbor query so ego-network traversal never loads the whole connection graph.
     */
    @Query("select c from Connection c where c.status = com.nukkad.user.entity.Connection.Status.ACCEPTED "
            + "and (c.userAId in :ids or c.userBId in :ids)")
    List<Connection> findAcceptedInvolvingAny(@Param("ids") Collection<String> ids);
}
