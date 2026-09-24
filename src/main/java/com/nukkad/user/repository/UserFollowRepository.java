package com.nukkad.user.repository;

import com.nukkad.user.entity.UserFollow;
import com.nukkad.user.entity.UserFollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollowId> {
    boolean existsByFollowerIdAndFolloweeId(String followerId, String followeeId);
    void deleteByFollowerIdAndFolloweeId(String followerId, String followeeId);

    /** Everyone this user follows — feeds the personalized feed's "posts from followed creators"
     *  candidate bucket. */
    @Query("select f.followeeId from UserFollow f where f.followerId = :followerId")
    List<String> findFolloweeIdsByFollowerId(@Param("followerId") String followerId);
}
