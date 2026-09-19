package com.nukkad.grant.repository;

import com.nukkad.common.moderation.ModerationStatus;
import com.nukkad.grant.entity.Grant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GrantRepository extends JpaRepository<Grant, String>, JpaSpecificationExecutor<Grant> {

    long countByModerationStatus(ModerationStatus moderationStatus);
}
