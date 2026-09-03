package com.nukkad.messaging.controller;

import com.nukkad.common.response.ApiResponse;
import com.nukkad.common.response.PageResponse;
import com.nukkad.messaging.dto.AddGroupMembersRequest;
import com.nukkad.messaging.dto.ConversationDto;
import com.nukkad.messaging.dto.CreateGroupRequest;
import com.nukkad.messaging.dto.MessageDto;
import com.nukkad.messaging.dto.SendMessageRequest;
import com.nukkad.messaging.dto.SetNicknameRequest;
import com.nukkad.messaging.dto.StartConversationRequest;
import com.nukkad.messaging.dto.UpdateGroupRequest;
import com.nukkad.messaging.dto.UpdateGroupRoleRequest;
import com.nukkad.messaging.dto.UpdateMessageRequest;
import com.nukkad.messaging.service.ConversationService;
import com.nukkad.messaging.service.GroupConversationService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {

    private final ConversationService conversationService;
    private final GroupConversationService groupConversationService;

    public ConversationController(ConversationService conversationService, GroupConversationService groupConversationService) {
        this.conversationService = conversationService;
        this.groupConversationService = groupConversationService;
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
                return ApiResponse.ok(conversationService.sendMessage(id, principal.id(), request.content(),
                        request.sharedPostId(), request.replyToMessageId()));
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

    /** "Delete for me": hides this message from the caller's own view only. See {@link ConversationService#hideMessagesForViewer}. */
    @DeleteMapping("/{id}/messages/{messageId}")
    public ApiResponse<Void> hideMessage(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @PathVariable String id, @PathVariable String messageId) {
        conversationService.hideMessagesForViewer(id, principal.id(), List.of(messageId));
        return ApiResponse.ok(null);
    }

    /** Bulk "delete for me": hides the given messages from the caller's own view only. */
    @DeleteMapping("/{id}/messages")
    public ApiResponse<Void> hideMessages(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable String id,
                                           @RequestParam List<String> messageIds) {
        conversationService.hideMessagesForViewer(id, principal.id(), messageIds);
        return ApiResponse.ok(null);
    }

    /** Edit: sender-only, enforced in {@link ConversationService#editMessage} from the authenticated
     * identity, never from a client-supplied id. */
    @PatchMapping("/{id}/messages/{messageId}")
    public ApiResponse<MessageDto> editMessage(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable String id, @PathVariable String messageId,
                                                @Valid @RequestBody UpdateMessageRequest request) {
        return ApiResponse.ok(conversationService.editMessage(id, principal.id(), messageId, request.content()));
    }

    /** Unsend: removes the message for everyone. Distinct from {@link #hideMessage} ("delete for me"),
     * which only ever changes the caller's own view. Sender-only, enforced in the service layer. */
    @PostMapping("/{id}/messages/{messageId}/unsend")
    public ApiResponse<MessageDto> unsendMessage(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable String id, @PathVariable String messageId) {
        return ApiResponse.ok(conversationService.unsendMessage(id, principal.id(), messageId));
    }

    @PostMapping("/group")
    public ApiResponse<ConversationDto> createGroup(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @Valid @RequestBody CreateGroupRequest request) {
        return ApiResponse.ok(groupConversationService.createGroup(principal.id(), request.name(), request.memberIds()));
    }

    @PatchMapping("/{id}/group")
    public ApiResponse<ConversationDto> renameGroup(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable String id, @Valid @RequestBody UpdateGroupRequest request) {
        return ApiResponse.ok(groupConversationService.renameGroup(principal.id(), id, request.name()));
    }

    @PostMapping("/{id}/group/avatar")
    public ApiResponse<ConversationDto> setGroupAvatar(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @PathVariable String id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(groupConversationService.setGroupAvatar(principal.id(), id, file));
    }

    @PostMapping("/{id}/group/members")
    public ApiResponse<ConversationDto> addGroupMembers(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable String id, @Valid @RequestBody AddGroupMembersRequest request) {
        return ApiResponse.ok(groupConversationService.addMembers(principal.id(), id, request.memberIds()));
    }

    @DeleteMapping("/{id}/group/members/{userId}")
    public ApiResponse<ConversationDto> removeGroupMember(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @PathVariable String id, @PathVariable String userId) {
        return ApiResponse.ok(groupConversationService.removeMember(principal.id(), id, userId));
    }

    @PostMapping("/{id}/group/leave")
    public ApiResponse<Void> leaveGroup(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
        groupConversationService.leaveGroup(principal.id(), id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/group/members/{userId}/role")
    public ApiResponse<ConversationDto> updateGroupRole(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable String id, @PathVariable String userId,
                                                          @Valid @RequestBody UpdateGroupRoleRequest request) {
        return ApiResponse.ok(groupConversationService.updateRole(principal.id(), id, userId, request.role()));
    }
}
