package com.nukkad.matching.service;

import com.nukkad.idea.entity.Idea;
import com.nukkad.idea.entity.IdeaStage;
import com.nukkad.idea.mapper.IdeaMapper;
import com.nukkad.idea.repository.IdeaInterestRepository;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.matching.dto.IdeaMatchDto;
import com.nukkad.opportunity.mapper.OpportunityMapper;
import com.nukkad.opportunity.repository.OpportunityApplicantRepository;
import com.nukkad.opportunity.repository.OpportunityInterestRepository;
import com.nukkad.opportunity.repository.OpportunityRepository;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserBlockRepository;
import com.nukkad.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers the "Recommended for you" idea-matching candidate filter — specifically that it never
 * recommends an idea that has already become a startup (mirrors the existing !opp.isClosed()
 * exclusion in rankOpportunitiesForUser; ideas had no equivalent exclusion before this fix).
 */
@ExtendWith(MockitoExtension.class)
class ContentMatchingServiceTest {

    @Mock private IdeaRepository ideaRepository;
    @Mock private IdeaInterestRepository ideaInterestRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @Mock private OpportunityApplicantRepository opportunityApplicantRepository;
    @Mock private OpportunityInterestRepository opportunityInterestRepository;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private UserService userService;

    private final IdeaMapper ideaMapper = new IdeaMapper();

    private ContentMatchingService service() {
        return new ContentMatchingService(ideaRepository, ideaInterestRepository, ideaMapper, opportunityRepository,
                opportunityApplicantRepository, opportunityInterestRepository, opportunityMapper, userBlockRepository,
                userService);
    }

    private Idea idea(String id, String creatorId) {
        return Idea.builder().id(id).title("Idea " + id).problem("A real problem worth solving")
                .solution("A solution").stage(IdeaStage.CONCEPT).creatorId(creatorId)
                .tags(new HashSet<>()).helpNeeded(new HashSet<>())
                .teamMemberIds(new HashSet<>(Set.of(creatorId))).build();
    }

    private User user(String id) {
        return User.builder().id(id).name("Viewer").skills(new HashSet<>()).build();
    }

    private void stubCommon(String viewerId, List<Idea> candidates) {
        when(userService.getEntityOrThrow(viewerId)).thenReturn(user(viewerId));
        when(userBlockRepository.findBlockedEitherWayIds(viewerId)).thenReturn(Set.of());
        when(ideaRepository.findAll(any(Pageable.class))).thenReturn((Page<Idea>) new PageImpl<>(candidates));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);
    }

    @Test
    void excludesIdeasThatHaveAlreadyBecomeAStartup() {
        Idea openIdea = idea("idea-open", "creator1");
        Idea convertedIdea = idea("idea-converted", "creator2");
        convertedIdea.setStartupId("startup1");
        stubCommon("viewer1", List.of(openIdea, convertedIdea));

        List<IdeaMatchDto> results = service().rankIdeasForUser("viewer1", 10);

        assertThat(results).extracting(m -> m.idea().id()).containsExactly("idea-open");
    }

    @Test
    void excludesTheViewersOwnIdeas() {
        Idea ownIdea = idea("idea-own", "viewer1");
        Idea othersIdea = idea("idea-others", "creator2");
        stubCommon("viewer1", List.of(ownIdea, othersIdea));

        List<IdeaMatchDto> results = service().rankIdeasForUser("viewer1", 10);

        assertThat(results).extracting(m -> m.idea().id()).containsExactly("idea-others");
    }

    @Test
    void excludesIdeasWhereViewerIsAlreadyOnTheTeam() {
        Idea teamIdea = idea("idea-team", "creator1");
        teamIdea.setTeamMemberIds(new HashSet<>(Set.of("creator1", "viewer1")));
        Idea othersIdea = idea("idea-others", "creator2");
        stubCommon("viewer1", List.of(teamIdea, othersIdea));

        List<IdeaMatchDto> results = service().rankIdeasForUser("viewer1", 10);

        assertThat(results).extracting(m -> m.idea().id()).containsExactly("idea-others");
    }

    @Test
    void excludesIdeasFromBlockedCreators() {
        Idea blockedIdea = idea("idea-blocked", "creator-blocked");
        Idea othersIdea = idea("idea-others", "creator2");
        when(userService.getEntityOrThrow("viewer1")).thenReturn(user("viewer1"));
        when(userBlockRepository.findBlockedEitherWayIds("viewer1")).thenReturn(Set.of("creator-blocked"));
        when(ideaRepository.findAll(any(Pageable.class))).thenReturn((Page<Idea>) new PageImpl<>(List.of(blockedIdea, othersIdea)));
        when(ideaInterestRepository.countByIdeaIdAndStatusNotIn(any(), any())).thenReturn(0L);

        List<IdeaMatchDto> results = service().rankIdeasForUser("viewer1", 10);

        assertThat(results).extracting(m -> m.idea().id()).containsExactly("idea-others");
    }
}
