package com.nukkad.investor.service;

import com.nukkad.common.audit.AuditAction;
import com.nukkad.common.audit.AuditService;
import com.nukkad.common.exception.BadRequestException;
import com.nukkad.common.exception.ConflictException;
import com.nukkad.common.exception.ForbiddenException;
import com.nukkad.common.exception.ResourceNotFoundException;
import com.nukkad.idea.entity.Idea;
import com.nukkad.idea.repository.IdeaRepository;
import com.nukkad.investor.dto.CreateIntroRequestRequest;
import com.nukkad.investor.dto.IntroRequestDto;
import com.nukkad.investor.entity.IntroDirection;
import com.nukkad.investor.entity.IntroRequest;
import com.nukkad.investor.entity.IntroRequestStatus;
import com.nukkad.investor.mapper.InvestorMapper;
import com.nukkad.investor.repository.IntroRequestRepository;
import com.nukkad.investor.repository.InvestorProfileRepository;
import com.nukkad.messaging.entity.Conversation;
import com.nukkad.messaging.repository.ConversationRepository;
import com.nukkad.messaging.service.ConversationService;
import com.nukkad.notification.entity.NotificationType;
import com.nukkad.notification.service.NotificationService;
import com.nukkad.startup.entity.Startup;
import com.nukkad.startup.repository.StartupRepository;
import com.nukkad.startup.service.StartupAccessPolicy;
import com.nukkad.user.dto.UserDto;
import com.nukkad.user.repository.UserRepository;
import com.nukkad.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class IntroRequestService {

    private final IntroRequestRepository introRequestRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final StartupRepository startupRepository;
    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final InvestorMapper investorMapper;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final StartupAccessPolicy startupAccessPolicy;

    public IntroRequestService(IntroRequestRepository introRequestRepository,
                                InvestorProfileRepository investorProfileRepository,
                                StartupRepository startupRepository,
                                IdeaRepository ideaRepository,
                                UserRepository userRepository,
                                UserService userService,
                                InvestorMapper investorMapper,
                                NotificationService notificationService,
                                AuditService auditService,
                                ConversationService conversationService,
                                ConversationRepository conversationRepository,
                                StartupAccessPolicy startupAccessPolicy) {
        this.introRequestRepository = introRequestRepository;
        this.investorProfileRepository = investorProfileRepository;
        this.startupRepository = startupRepository;
        this.ideaRepository = ideaRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.investorMapper = investorMapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.startupAccessPolicy = startupAccessPolicy;
    }

    public IntroRequest getEntityOrThrow(String id) {
        return introRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Introduction request not found: " + id));
    }

    @Transactional
    public IntroRequestDto create(String requesterId, CreateIntroRequestRequest request) {
        if (requesterId.equals(request.recipientId())) {
            throw new BadRequestException("You cannot request an introduction to yourself");
        }
        IntroDirection direction;
        try {
            direction = IntroDirection.valueOf(request.direction());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown direction: " + request.direction());
        }
        boolean founderRequests = direction == IntroDirection.FOUNDER_TO_INVESTOR;
        String founderId = founderRequests ? requesterId : request.recipientId();
        String investorId = founderRequests ? request.recipientId() : requesterId;

        // Lock the investor's row first, so two simultaneous requests for the same founder/startup/investor
        // cannot both get past the duplicate check below. It must come before any other read in this transaction.
        userRepository.findByIdForUpdate(investorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + investorId));
        userRepository.findById(request.recipientId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.recipientId()));

        if (direction == IntroDirection.FOUNDER_TO_INVESTOR) {
            if (!investorProfileRepository.existsByUserId(request.recipientId())) {
                throw new ResourceNotFoundException("This user does not have an investor profile");
            }
        } else {
            if (!investorProfileRepository.existsByUserId(requesterId)) {
                throw new ForbiddenException("You need an investor profile to reach out to founders");
            }
        }

        String startupId = blankToNull(request.startupId());
        String ideaId = blankToNull(request.ideaId());
        if (startupId != null) {
            // The startup must exist and be readable by the requester (a removed or rejected one is a plain 404)...
            startupAccessPolicy.requireReadable(startupId, requesterId);
            // ...and the founder side of the request must actually run it, or anyone could cite someone else's startup.
            if (!startupAccessPolicy.canManage(startupId, founderId)) {
                if (founderRequests) {
                    throw new ForbiddenException("You can only request an introduction on behalf of a startup you manage");
                }
                throw new BadRequestException("That founder does not manage this startup");
            }
        }
        if (ideaId != null && !ideaRepository.existsById(ideaId)) {
            throw new ResourceNotFoundException("Idea not found: " + ideaId);
        }

        rejectDuplicate(founderId, investorId, startupId, ideaId);

        IntroRequest entity = IntroRequest.builder()
                .requesterId(requesterId)
                .recipientId(request.recipientId())
                .direction(direction)
                .startupId(startupId)
                .ideaId(ideaId)
                .message(request.message().trim())
                .build();
        entity = introRequestRepository.saveAndFlush(entity);

        auditService.log(requesterId, AuditAction.INVESTOR_INTRODUCTION, "IntroRequest", entity.getId(), null);
        notificationService.notify(entity.getRecipientId(), NotificationType.investor,
                "New introduction request", "Someone requested an introduction", entity.getId(), requesterId);

        return toDto(entity, requesterId);
    }

    /**
     * One live request per founder + startup (or idea) + investor combination, whichever of the two started it: while one
     * is pending or accepted, another for the same combination is refused. A declined or withdrawn request no longer counts,
     * so the founder can ask again. A different startup with the same investor is a different combination.
     */
    private void rejectDuplicate(String founderId, String investorId, String startupId, String ideaId) {
        for (IntroRequest existing : introRequestRepository.findActiveBetween(founderId, investorId)) {
            if (!Objects.equals(existing.getStartupId(), startupId) || !Objects.equals(existing.getIdeaId(), ideaId)) {
                continue;
            }
            String about = startupId != null ? " for this startup" : ideaId != null ? " for this idea" : "";
            if (existing.getStatus() == IntroRequestStatus.ACCEPTED) {
                throw new ConflictException("An introduction" + about + " has already been accepted. Continue the conversation in Messages.");
            }
            throw new ConflictException("There is already a pending introduction request" + about + " between you and this investor");
        }
    }

    @Transactional(readOnly = true)
    public IntroRequestDto get(String id, String viewerId) {
        IntroRequest request = getEntityOrThrow(id);
        requireParticipant(viewerId, request);
        return toDto(request, viewerId);
    }

    @Transactional(readOnly = true)
    public List<IntroRequestDto> inbox(String userId) {
        return introRequestRepository.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> toDto(r, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IntroRequestDto> sent(String userId) {
        return introRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> toDto(r, userId))
                .toList();
    }

    @Transactional
    public IntroRequestDto accept(String userId, String id) {
        IntroRequest request = getEntityOrThrow(id);
        if (!userId.equals(request.getRecipientId())) {
            throw new ForbiddenException("Only the recipient can accept this request");
        }
        requirePending(request);
        request.setStatus(IntroRequestStatus.ACCEPTED);
        request.setReviewedAt(Instant.now());
        request = introRequestRepository.saveAndFlush(request);

        // Accepting an introduction is what unlocks messaging between these two users, so the
        // conversation is opened right here as part of accepting it, rather than leaving it to the
        // frontend to make a second call — a direct API caller gets the same guaranteed result.
        String conversationId = conversationService.getOrCreate(request.getRecipientId(), request.getRequesterId()).id();

        notificationService.notify(request.getRequesterId(), NotificationType.investor,
                "Introduction accepted", "Your introduction request was accepted", request.getId(), userId);

        return toDto(request, userId, conversationId);
    }

    @Transactional
    public IntroRequestDto reject(String userId, String id) {
        IntroRequest request = getEntityOrThrow(id);
        if (!userId.equals(request.getRecipientId())) {
            throw new ForbiddenException("Only the recipient can decline this request");
        }
        requirePending(request);
        request.setStatus(IntroRequestStatus.REJECTED);
        request.setReviewedAt(Instant.now());
        request = introRequestRepository.saveAndFlush(request);

        notificationService.notify(request.getRequesterId(), NotificationType.investor,
                "Introduction declined", "Your introduction request wasn't accepted this time", request.getId(), userId);

        return toDto(request, userId);
    }

    @Transactional
    public IntroRequestDto withdraw(String userId, String id) {
        IntroRequest request = getEntityOrThrow(id);
        if (!userId.equals(request.getRequesterId())) {
            throw new ForbiddenException("Only the requester can withdraw this request");
        }
        requirePending(request);
        request.setStatus(IntroRequestStatus.WITHDRAWN);
        request.setReviewedAt(Instant.now());
        return toDto(introRequestRepository.saveAndFlush(request), userId);
    }

    private void requirePending(IntroRequest request) {
        if (request.getStatus().isTerminal()) {
            throw new BadRequestException("This request has already been decided");
        }
    }

    private void requireParticipant(String userId, IntroRequest request) {
        if (!userId.equals(request.getRequesterId()) && !userId.equals(request.getRecipientId())) {
            throw new ForbiddenException("You are not part of this introduction request");
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private IntroRequestDto toDto(IntroRequest request, String viewerId) {
        String conversationId = request.getStatus() == IntroRequestStatus.ACCEPTED
                ? findExistingConversationId(request.getRequesterId(), request.getRecipientId())
                : null;
        return toDto(request, viewerId, conversationId);
    }

    private IntroRequestDto toDto(IntroRequest request, String viewerId, String conversationId) {
        UserDto requester = userService.getUser(request.getRequesterId(), viewerId);
        UserDto recipient = userService.getUser(request.getRecipientId(), viewerId);
        String startupName = request.getStartupId() == null ? null
                : startupRepository.findById(request.getStartupId()).map(Startup::getName).orElse(null);
        String ideaTitle = request.getIdeaId() == null ? null
                : ideaRepository.findById(request.getIdeaId()).map(Idea::getTitle).orElse(null);
        return investorMapper.toDto(request, requester, recipient, startupName, ideaTitle, conversationId);
    }

    private String findExistingConversationId(String userId1, String userId2) {
        String a = userId1.compareTo(userId2) < 0 ? userId1 : userId2;
        String b = userId1.compareTo(userId2) < 0 ? userId2 : userId1;
        return conversationRepository.findByUserAIdAndUserBId(a, b).map(Conversation::getId).orElse(null);
    }
}
