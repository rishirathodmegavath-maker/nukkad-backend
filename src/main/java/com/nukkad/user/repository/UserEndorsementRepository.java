package com.nukkad.user.repository;

import com.nukkad.user.entity.UserEndorsement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserEndorsementRepository extends JpaRepository<UserEndorsement, String> {

    boolean existsByEndorsedUserIdAndEndorserUserIdAndSkill(String endorsedUserId, String endorserUserId, String skill);

    void deleteByEndorsedUserIdAndEndorserUserIdAndSkill(String endorsedUserId, String endorserUserId, String skill);

    long countByEndorsedUserIdAndSkill(String endorsedUserId, String skill);

    @Query("select e.skill as skill, count(e) as count from UserEndorsement e where e.endorsedUserId = :endorsedUserId group by e.skill")
    List<SkillCount> countGroupedBySkill(@Param("endorsedUserId") String endorsedUserId);

    @Query("select e.skill from UserEndorsement e where e.endorsedUserId = :endorsedUserId and e.endorserUserId = :endorserUserId")
    List<String> findEndorsedSkills(@Param("endorsedUserId") String endorsedUserId, @Param("endorserUserId") String endorserUserId);

    interface SkillCount {
        String getSkill();
        long getCount();
    }
}
