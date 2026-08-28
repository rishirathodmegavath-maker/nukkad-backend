package com.nukkad.event.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.event.dto.CreateEventRequest;
import com.nukkad.event.dto.EventDto;
import com.nukkad.event.dto.UpdateEventRequest;
import com.nukkad.event.service.EventService;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.user.dto.UserDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ApiResponse<PageResponse<EventDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @RequestParam(required = false) String chapterId,
                                                       @RequestParam(required = false) Boolean upcoming,
                                                       @RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String organizerUserId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                eventService.listEvents(chapterId, upcoming, q, organizerUserId, principal.id(), page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(eventService.getEvent(id, principal.id()));
    }

    @GetMapping("/{id}/attendees")
    public ApiResponse<List<UserDto>> attendees(@PathVariable String id) {
        return ApiResponse.ok(eventService.getAttendees(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @Valid @RequestBody CreateEventRequest request) {
        return ApiResponse.ok(eventService.createEvent(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<EventDto> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @PathVariable String id,
                                          @Valid @RequestBody UpdateEventRequest request) {
        return ApiResponse.ok(eventService.updateEvent(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        eventService.deleteEvent(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/rsvp")
    public ApiResponse<EventDto> rsvp(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(eventService.rsvp(principal.id(), id));
    }

    @PostMapping("/{id}/cancel-rsvp")
    public ApiResponse<EventDto> cancelRsvp(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(eventService.cancelRsvp(principal.id(), id));
    }
}
