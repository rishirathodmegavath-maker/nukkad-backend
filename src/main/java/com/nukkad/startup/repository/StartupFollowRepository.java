package com.nukkad.startup.repository;

import com.nukkad.startup.entity.StartupFollow;
import com.nukkad.startup.entity.StartupFollowId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StartupFollowRepository extends JpaRepository<StartupFollow, StartupFollowId> {
    boolean existsByUserIdAndStartupId(String userId, String startupId);
    void deleteByUserIdAndStartupId(String userId, String startupId);
}
