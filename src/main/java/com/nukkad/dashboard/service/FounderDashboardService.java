package com.nukkad.dashboard.service;

import com.nukkad.dashboard.dto.FounderDashboardDto;
import com.nukkad.dashboard.dto.FounderDashboardDto.StartupMetrics;
import com.nukkad.event.repository.EventAttendeeRepository;
import com.nukkad.investor.entity.IntroDirection;
import com.nukkad.investor.entity.IntroRequestStatus;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Real numbers for the founder dashboard. It covers every startup the user runs — as founder or admin, the same tier that
 * may edit a startup — and reports each one separately as well as the totals, so no startup is silently ignored.
 * Everything is counted per startup: profile views and followers of the startup, investors who reached out about it,
 * applications to jobs attributed to it, and RSVPs to the events it is tagged on (its own team's RSVPs excluded).
 */
@Service
public class FounderDashboardService {

    /** Founder + Admin — the tier that manages a startup. */
    private static final List<StartupTeamMember.TeamRole> MANAGER_ROLES =
            List.of(StartupTeamMember.TeamRole.FOUNDER, StartupTeamMember.TeamRole.ADMIN);

    private static final FounderDashboardDto EMPTY = new FounderDashboardDto(false, null, null, 0, 0, 0, 0, 0, 0, 0, List.of());

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
        Map<String, StartupTeamMember.TeamRole> roleByStartup = teamMemberRepository
                .findByUserIdAndTeamRoleInAndStatus(userId, MANAGER_ROLES, StartupTeamMember.Status.ACTIVE).stream()
                .collect(Collectors.toMap(StartupTeamMember::getStartupId, StartupTeamMember::getTeamRole, (a, b) -> a));
        if (roleByStartup.isEmpty()) {
            return EMPTY;
        }

        // A startup an admin removed is gone from the product (its page is a 404), so it is not shown here either.
        List<Startup> startups = startupRepository.findAllById(roleByStartup.keySet()).stream()
                .filter(s -> !s.isRemovedByAdmin())
                .sorted(Comparator
                        .comparing((Startup s) -> roleByStartup.get(s.getId()) == StartupTeamMember.TeamRole.FOUNDER ? 0 : 1)
                        .thenComparing(Startup::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (startups.isEmpty()) {
            return EMPTY;
        }

        List<StartupMetrics> items = startups.stream().map(s -> metricsFor(s, roleByStartup.get(s.getId()))).toList();
        int avgCompletion = (int) Math.round(items.stream().mapToInt(StartupMetrics::profileCompletionPercent).average().orElse(0));
        StartupMetrics primary = items.get(0);
        return new FounderDashboardDto(true, primary.id(), primary.name(), items.size(),
                sum(items, StartupMetrics::profileViews),
                sum(items, StartupMetrics::investorInterests),
                sum(items, StartupMetrics::jobApplications),
                sum(items, StartupMetrics::followers),
                sum(items, StartupMetrics::eventRsvps),
                avgCompletion, items);
    }

    private StartupMetrics metricsFor(Startup startup, StartupTeamMember.TeamRole role) {
        String id = startup.getId();
        return new StartupMetrics(
                id,
                startup.getName(),
                startup.getLogoUrl(),
                startup.getStage().getLabel(),
                startup.isRaising(),
                role.name(),
                profileViewRepository.countByStartupId(id),
                followRepository.countByStartupId(id),
                introRequestRepository.countByStartupIdAndDirectionAndStatusNot(id, IntroDirection.INVESTOR_TO_FOUNDER, IntroRequestStatus.WITHDRAWN),
                applicantRepository.countByStartupId(id),
                eventAttendeeRepository.countRsvpsForStartupEvents(id, StartupTeamMember.Status.ACTIVE),
                startupMapper.profileCompletionPercent(startup));
    }

    private static long sum(List<StartupMetrics> items, Function<StartupMetrics, Long> field) {
        return items.stream().mapToLong(field::apply).sum();
    }
}
