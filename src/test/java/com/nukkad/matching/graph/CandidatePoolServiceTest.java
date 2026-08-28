package com.nukkad.matching.graph;

import com.nukkad.matching.graph.CandidatePoolService.CandidatePool;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserBlockRepository;
import com.nukkad.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression test for a real crash found via seeded live testing at realistic scale: when the
 * chapter-scoped fallback query doesn't return enough candidates, a second "widened" query tops up the
 * list — but {@code Page.getContent()} is not guaranteed mutable, so appending to it directly threw
 * {@link UnsupportedOperationException} in production once a chapter had enough members to make the
 * first query's result immutable-but-short. Never reproduced with the original 3-user dev dataset.
 */
@ExtendWith(MockitoExtension.class)
class CandidatePoolServiceTest {

    @Mock
    private EgoNetworkService egoNetworkService;
    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockRepository userBlockRepository;

    @Test
    void fallbackWidenDoesNotMutateUnmodifiablePageContent() {
        User viewer = User.builder().id("viewer").name("Viewer").chapterId("chapter-1").build();
        User extra = User.builder().id("extra").name("Extra").build();

        when(egoNetworkService.build("viewer", 2))
                .thenReturn(new EgoNetworkService.EgoNetwork(Map.of(), Set.of(), Map.of()));
        when(userBlockRepository.findBlockedEitherWayIds("viewer")).thenReturn(Set.of());
        when(connectionRepository.findAllInvolving("viewer")).thenReturn(List.of());

        // First (chapter-scoped) call returns an UNMODIFIABLE empty page — this is exactly what
        // triggers the widen path in production. Second call is the widened, unscoped query.
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()), new PageImpl<>(List.of(extra)));

        CandidatePoolService service = new CandidatePoolService(egoNetworkService, connectionRepository, userRepository, userBlockRepository);

        CandidatePool pool = service.gather(viewer, 5);

        assertThat(pool.allCandidates()).extracting(User::getId).containsExactly("extra");
    }
}
