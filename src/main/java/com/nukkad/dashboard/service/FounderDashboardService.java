package com.nukkad.dashboard.service;

import com.nukkad.dashboard.dto.FounderDashboardDto;
import com.nukkad.event.repository.EventAttendeeRepository;
import com.nukkad.investor.entity.IntroDirection;
import com.nukkad.investor.repository.IntroRequestRepository;
import com.nukkad.opportunity.repository.OpportunityApplicantRepository;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.entity.StartupTeamMember;
import com.nukkad.startup.mapper.StartupMapper;
import com.nukkad.startup.repository.StartupFollowRepository;
import com.nukkad.startup.repository.StartupProfileViewRepository;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.repository.StartupTeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FounderDashboardService {

    private static final FounderDashboardDto EMPTY = new FounderDashboardDto(false, null, null, 0, 0, 0, 0, 0, 0, 0);

    private final StartupTeamMemberRepository teamMemberRepository;
    private final StartupRepository startupRepository;
    private final StartupFollowRepository followRepository;
    private final StartupProfileViewRepository profileViewRepository;
    private final IntroRequestRepository introRequestRepository;
    private final OpportunityApplicantRepository applicantRepository;
    private final EventAttendeeRepository eventAttendeeRepository;
    private final StartupMapper startupMapper;

    public FounderDashboardService(StartupTeamMemberRepository teamMemberRepository,
                                    StartupRepository startupRepository,
                                    StartupFollowRepository followRepository,
                                    StartupProfileViewRepository profileViewRepository,
                                    IntroRequestRepository introRequestRepository,
                                    OpportunityApplicantRepository applicantRepository,
                                    EventAttendeeRepository eventAttendeeRepository,
                                    StartupMapper startupMapper) {
        this.teamMemberRepository = teamMemberRepository;
        this.startupRepository = startupRepository;
        this.followRepository = followRepository;
        this.profileViewRepository = profileViewRepository;
        this.introRequestRepository = introRequestRepository;
        this.applicantRepository = applicantRepository;
        this.eventAttendeeRepository = eventAttendeeRepository;
        this.startupMapper = startupMapper;
    }

    @Transactional(readOnly = true)
    public FounderDashboardDto getDashboard(String userId) {
        List<String> startupIds = teamMemberRepository
                .findByUserIdAndTeamRoleInAndStatus(userId, List.of(StartupTeamMember.TeamRole.FOUNDER), StartupTeamMember.Status.ACTIVE)
                .stream().map(StartupTeamMember::getStartupId).toList();
        if (startupIds.isEmpty()) {
            return EMPTY;
        }

        List<Startup> startups = startupRepository.findAllById(startupIds);

        long profileViews = startupIds.stream().mapToLong(profileViewRepository::countByStartupId).sum();
        long followers = startupIds.stream().mapToLong(followRepository::countByStartupId).sum();
        long investorInterests = introRequestRepository.countByRecipientIdAndDirection(userId, IntroDirection.INVESTOR_TO_FOUNDER);
        long jobApplications = applicantRepository.countByPostedByUserId(userId);
        long eventRsvps = eventAttendeeRepository.countByUserId(userId);

        int avgCompletion = startups.isEmpty() ? 0 : (int) Math.round(
                startups.stream()
                        .mapToInt(s -> startupMapper.toDto(s, false, true, true).profileCompletionPercent())
                        .average()
                        .orElse(0));

        Startup primary = startups.get(0);
        return new FounderDashboardDto(true, primary.getId(), primary.getName(), startups.size(),
                profileViews, investorInterests, jobApplications, followers, eventRsvps, avgCompletion);
    }
}
