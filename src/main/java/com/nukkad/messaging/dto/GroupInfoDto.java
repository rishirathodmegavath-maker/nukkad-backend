package com.nukkad.messaging.dto;

import java.util.List;

public record GroupInfoDto(String name, String avatarUrl, String createdBy, String myRole, List<GroupParticipantDto> participants) {
}
