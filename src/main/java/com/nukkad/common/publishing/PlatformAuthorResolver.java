package com.nukkad.common.publishing;

import com.nukkad.common.exception.BadRequestException;
import com.nukkad.user.entity.AccountStatus;
import com.nukkad.user.entity.User;
import com.nukkad.user.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * The shared "who does an admin-created row belong to" resolution every admin-create endpoint needs
 * (Feed already had this as a private method; extracted here so Ideas/Startups/Opportunities/Grants/
 * Events/Resources/Discussions don't each reimplement it). With {@code authorEmail}, that member
 * becomes the real owner (and is told, by whatever notification the caller sends); without it, the
 * admin's own account is the owner, which the caller should then treat as unattributed platform
 * content (see each entity's own {@code postedAsPlatform}-style flag, set by comparing the resolved
 * id back against {@code adminId} — never a separate signal, so the two can never drift apart).
 */
@Service
public class PlatformAuthorResolver {

    private final UserRepository userRepository;

    public PlatformAuthorResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String resolve(String adminId, String authorEmail) {
        if (authorEmail == null || authorEmail.isBlank()) return adminId;
        User author = userRepository.findByEmail(authorEmail.toLowerCase().trim())
                .orElseThrow(() -> new BadRequestException("No member has that email address"));
        if (author.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("That member's account is not active");
        }
        return author.getId();
    }
}
