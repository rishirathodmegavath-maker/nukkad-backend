package com.nukkad.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_privacy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPrivacySettings {

    @Id
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false, length = 20)
    @Builder.Default
    private ProfileVisibility profileVisibility = ProfileVisibility.EVERYONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_permission", nullable = false, length = 20)
    @Builder.Default
    private MessagePermission messagePermission = MessagePermission.EVERYONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "connect_permission", nullable = false, length = 20)
    @Builder.Default
    private ConnectPermission connectPermission = ConnectPermission.EVERYONE;
}
