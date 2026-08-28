package com.nukkad.resource.repository;

import com.nukkad.resource.entity.ResourceSave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface ResourceSaveRepository extends JpaRepository<ResourceSave, String> {
    Optional<ResourceSave> findByResourceIdAndUserId(String resourceId, String userId);

    @Query("select s.resourceId from ResourceSave s where s.userId = :userId and s.resourceId in :resourceIds")
    Set<String> findSavedResourceIds(@Param("userId") String userId, @Param("resourceIds") Collection<String> resourceIds);
}
