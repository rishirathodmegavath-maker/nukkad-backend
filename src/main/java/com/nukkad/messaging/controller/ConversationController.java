package com.nukkad.messaging.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.messaging.dto.ConversationDto;
import com.nukkad.messaging.dto.MessageDto;
import com.nukkad.messaging.dto.SendMessageRequest;
import com.nukkad.messaging.dto.SetNicknameRequest;
import com.nukkad.messaging.dto.StartConversationRequest;
import com.nukkad.messaging.service.ConversationService;
import com.nukkad.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ConversationDto>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.from(conversationService.list(principal.id(), page, size)));
    }

    @PostMapping
    public ApiResponse<ConversationDto> getOrCreate(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @Valid @RequestBody StartConversationRequest request) {
        return ApiResponse.ok(conversationService.getOrCreate(principal.id(), request.otherUserId()));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<PageResponse<MessageDto>> getMessages(@AuthenticationPrincipal AuthenticatedUser principal,
                                                               @PathVariable String id,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "100") int size) {
        return ApiResponse.ok(PageResponse.from(conversationService.getMessages(id, principal.id(), page, size)));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<MessageDto> sendMessage(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable String id,
                                                @Valid @RequestBody SendMessageRequest request) {
        // Two messages landing in the same conversation at the same instant can deadlock on the
        // conversation row's UPDATE (MySQL FK-locking between the messages insert and the
        // conversations update); retrying the whole transaction is the standard response to a
        // MySQL deadlock, which by design always aborts exactly one of the two contending transactions.
        int attempts = 0;
        while (true) {
            try {
                return ApiResponse.ok(conversationService.sendMessage(id, principal.id(), request.content(), request.sharedPostId()));
            } catch (PessimisticLockingFailureException ex) {
                if (++attempts >= 5) throw ex;
                try {
                    Thread.sleep((long) (Math.random() * 25 * attempts));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        conversationService.markRead(id, principal.id());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/mute")
    public ApiResponse<ConversationDto> toggleMute(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        return ApiResponse.ok(conversationService.toggleMute(id, principal.id()));
    }

    @PatchMapping("/{id}/nickname")
    public ApiResponse<ConversationDto> setNickname(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable String id,
                                                      @Valid @RequestBody SetNicknameRequest request) {
        return ApiResponse.ok(conversationService.setNickname(id, principal.id(), request.nickname()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        conversationService.deleteConversation(id, principal.id());
        return ApiResponse.ok(null);
    }
}
