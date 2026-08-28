package com.nukkad.matching.service;

import com.nukkad.idea.entity.ContributionArea;
import com.nukkad.idea.entity.Idea;
import com.nukkad.opportunity.entity.Opportunity;
import com.nukkad.user.entity.LookingFor;
import com.nukkad.user.entity.OpenTo;
import com.nukkad.user.entity.User;

import java.util.stream.Collectors;

/**
 * Builds the plain-text "document" each domain object is turned into for the TF-IDF engine.
 * User documents deliberately use only always-public top-level fields (headline, bio, goals, skills,
 * lookingFor, openTo) — none of these are gated by {@code ProfilePrivacyService}, so a candidate's
 * PRIVATE experience/education/project sections can never leak into a match score or reason.
 */
public final class ProfileTextBuilder {

    private ProfileTextBuilder() {
    }

    public static String forUser(User user) {
        StringBuilder text = new StringBuilder();
        appendIfPresent(text, user.getHeadline());
        appendIfPresent(text, user.getBio());
        appendIfPresent(text, user.getGoals());
        appendIfPresent(text, String.join(" ", user.getSkills()));
        appendIfPresent(text, user.getLookingFor().stream().map(LookingFor::getLabel).collect(Collectors.joining(" ")));
        appendIfPresent(text, user.getOpenTo().stream().map(OpenTo::getLabel).collect(Collectors.joining(" ")));
        return text.toString();
    }

    public static String forIdea(Idea idea) {
        StringBuilder text = new StringBuilder();
        appendIfPresent(text, idea.getTitle());
        appendIfPresent(text, idea.getProblem());
        appendIfPresent(text, idea.getSolution());
        appendIfPresent(text, idea.getTargetCustomer());
        appendIfPresent(text, idea.getCategory());
        appendIfPresent(text, String.join(" ", idea.getTags()));
        appendIfPresent(text, idea.getHelpNeeded().stream().map(ContributionArea::getLabel).collect(Collectors.joining(" ")));
        return text.toString();
    }

    public static String forOpportunity(Opportunity opportunity) {
        StringBuilder text = new StringBuilder();
        appendIfPresent(text, opportunity.getTitle());
        appendIfPresent(text, opportunity.getDescription());
        appendIfPresent(text, opportunity.getOrganizationName());
        appendIfPresent(text, opportunity.getType() == null ? null : opportunity.getType().getLabel());
        appendIfPresent(text, String.join(" ", opportunity.getRequirements()));
        return text.toString();
    }

    private static void appendIfPresent(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(value).append(' ');
        }
    }
}
