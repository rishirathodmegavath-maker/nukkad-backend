package com.nukkad.matching.graph;

import com.nukkad.matching.graph.EgoNetworkService.EgoNetwork;
import com.nukkad.user.entity.Connection;
import com.nukkad.user.repository.ConnectionRepository;
import com.nukkad.user.repository.UserBlockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Scenario from the product spec: A-B, A-C, B-D, C-D (all ACCEPTED).
 * From A's perspective: 1st-degree = {B, C}; 2nd-degree = {D}, reachable via both B and C,
 * so D should show exactly 2 mutual connections.
 */
@ExtendWith(MockitoExtension.class)
class EgoNetworkServiceTest {

    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private UserBlockRepository userBlockRepository;

    private Connection accepted(String a, String b) {
        return Connection.builder().userAId(a).userBId(b).status(Connection.Status.ACCEPTED).requestedBy(a).build();
    }

    @Test
    void twoHopScenarioMatchesSpecExample() {
        String a = "A", b = "B", c = "C", d = "D";

        when(connectionRepository.findAcceptedConnections(a)).thenReturn(List.of(accepted(a, b), accepted(a, c)));
        when(connectionRepository.findAcceptedInvolvingAny(any())).thenReturn(
                List.of(accepted(a, b), accepted(a, c), accepted(b, d), accepted(c, d)));
        when(connectionRepository.findAllInvolving(a)).thenReturn(List.of(accepted(a, b), accepted(a, c)));
        when(userBlockRepository.findBlockedEitherWayIds(a)).thenReturn(Set.of());

        EgoNetworkService service = new EgoNetworkService(connectionRepository, userBlockRepository);
        EgoNetwork network = service.build(a, 2);

        assertThat(network.firstDegreeIds()).containsExactlyInAnyOrder(b, c);
        assertThat(network.distanceByUserId()).containsEntry(d, 2);
        assertThat(network.distanceByUserId()).doesNotContainKeys(b, c, a);
        assertThat(network.mutualConnectionCount(d)).isEqualTo(2);
    }

    @Test
    void blockedCandidateIsExcludedFromSecondDegree() {
        String a = "A", b = "B", blocked = "BLOCKED";

        when(connectionRepository.findAcceptedConnections(a)).thenReturn(List.of(accepted(a, b)));
        when(connectionRepository.findAcceptedInvolvingAny(any())).thenReturn(List.of(accepted(a, b), accepted(b, blocked)));
        when(connectionRepository.findAllInvolving(a)).thenReturn(List.of(accepted(a, b)));
        when(userBlockRepository.findBlockedEitherWayIds(a)).thenReturn(Set.of(blocked));

        EgoNetworkService service = new EgoNetworkService(connectionRepository, userBlockRepository);
        EgoNetwork network = service.build(a, 2);

        assertThat(network.distanceByUserId()).doesNotContainKey(blocked);
    }

    @Test
    void alreadyPendingCandidateIsExcludedFromSecondDegree() {
        String a = "A", b = "B", pending = "PENDING_USER";

        when(connectionRepository.findAcceptedConnections(a)).thenReturn(List.of(accepted(a, b)));
        when(connectionRepository.findAcceptedInvolvingAny(any())).thenReturn(List.of(accepted(a, b), accepted(b, pending)));
        Connection pendingRequest = Connection.builder().userAId(a).userBId(pending).status(Connection.Status.PENDING).requestedBy(a).build();
        when(connectionRepository.findAllInvolving(a)).thenReturn(List.of(accepted(a, b), pendingRequest));
        when(userBlockRepository.findBlockedEitherWayIds(a)).thenReturn(Set.of());

        EgoNetworkService service = new EgoNetworkService(connectionRepository, userBlockRepository);
        EgoNetwork network = service.build(a, 2);

        assertThat(network.distanceByUserId()).doesNotContainKey(pending);
    }

    @Test
    void depthOneOnlyReturnsNoCandidates() {
        String a = "A", b = "B";
        when(connectionRepository.findAcceptedConnections(a)).thenReturn(List.of(accepted(a, b)));
        when(connectionRepository.findAllInvolving(a)).thenReturn(List.of(accepted(a, b)));
        when(userBlockRepository.findBlockedEitherWayIds(a)).thenReturn(Set.of());

        EgoNetworkService service = new EgoNetworkService(connectionRepository, userBlockRepository);
        EgoNetwork network = service.build(a, 1);

        assertThat(network.distanceByUserId()).isEmpty();
    }
}
