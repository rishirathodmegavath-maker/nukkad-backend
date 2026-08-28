package com.nukkad.user.repository;

import com.nukkad.user.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface UserBlockRepository extends JpaRepository<UserBlock, String> {
    boolean existsByBlockerIdAndBlockedId(String blockerId, String blockedId);
    void deleteByBlockerIdAndBlockedId(String blockerId, String blockedId);
    List<UserBlock> findByBlockerId(String blockerId);

    @Query("select case when count(b) > 0 then true else false end from UserBlock b "
            + "where (b.blockerId = :a and b.blockedId = :b) or (b.blockerId = :b and b.blockedId = :a)")
    boolean existsBetween(@Param("a") String a, @Param("b") String b);

    @Query("select b.blockedId from UserBlock b where b.blockerId = :viewerId "
            + "union select b.blockerId from UserBlock b where b.blockedId = :viewerId")
    Set<String> findBlockedEitherWayIds(@Param("viewerId") String viewerId);
}
