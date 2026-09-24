package com.nukkad.feed.repository;

import com.nukkad.feed.entity.UserTopicAffinity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserTopicAffinityRepository extends JpaRepository<UserTopicAffinity, String> {

    Optional<UserTopicAffinity> findByUserIdAndTopicKindAndTopicKey(
            String userId, UserTopicAffinity.TopicKind topicKind, String topicKey);

    /** Every topic this user has any affinity for — a user's own row is never contended by
     *  another user, so a plain find-then-save upsert (see UserTopicAffinityService) is safe
     *  without the atomic-counter trick {@code posts.likes_count} needs. */
    List<UserTopicAffinity> findByUserId(String userId);
}
