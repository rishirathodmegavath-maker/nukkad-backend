package com.nukkad.user.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserSectionPrivacyId implements Serializable {
    private String userId;
    private ProfileSection section;

    public UserSectionPrivacyId() {}

    public UserSectionPrivacyId(String userId, ProfileSection section) {
        this.userId = userId;
        this.section = section;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserSectionPrivacyId that)) return false;
        return Objects.equals(userId, that.userId) && section == that.section;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, section);
    }
}
