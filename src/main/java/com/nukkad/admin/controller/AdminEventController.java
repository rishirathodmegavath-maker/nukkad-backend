package com.nukkad.admin.controller;

import com.nukkad.admin.dto.AdminCreateEventRequest;
import com.nukkad.common.response.ApiResponse;
import com.nukkad.event.dto.CreateEventRequest;
import com.nukkad.event.dto.EventDto;
import com.nukkad.event.service.EventService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lets an admin publish an event directly, reusing EventService exactly as the public
 *  /api/events endpoints do (see EventService#createEventAsAdmin). Reading, listing, RSVPing and
 *  editing an event — admin-created or not — all continue through the existing member-facing
 *  /api/events endpoints; this only adds the one thing that never existed there: publishing one
 *  unattributed to any member, under a platform display identity. */
@RestController
@RequestMapping("/api/admin/events")
@SecurityRequirement(name = "bearerAuth")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @Valid @RequestBody AdminCreateEventRequest request,
                                         HttpServletRequest httpRequest) {
        CreateEventRequest event = new CreateEventRequest(request.title(), request.description(), request.chapterId(),
                request.startAt(), request.endAt(), request.online(), request.location(), request.meetingUrl(),
                request.coverImageUrl(), request.capacity(), null);
        return ApiResponse.ok(eventService.createEventAsAdmin(
                principal.id(), event, request.organizerEmail(), request.publisherIdentity(), httpRequest.getRemoteAddr()));
    }
}
