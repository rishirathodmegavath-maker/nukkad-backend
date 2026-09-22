package com.nukkad.startup.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "Raising Now" discovery filter must follow the same rule as the {@code isRaising} field a viewer is shown:
 * a startup counts as raising only for someone who may see its fundraising (everyone while the founders keep
 * fundraising visible, otherwise only its active team). See {@link CriteriaRecorder} for how the rule is read.
 */
class StartupSpecificationsTest {

    @Test
    void noFilterMeansNoSpecification() {
        assertThat(StartupSpecifications.isRaisingAsSeenBy(null, "viewer-1")).isNull();
        assertThat(StartupSpecifications.isRaisingAsSeenBy(null, null)).isNull();
    }

    @Test
    void raisingForAnAnonymousViewerRequiresVisibleFundraisingAndNeverConsultsTheTeam() {
        CriteriaRecorder recorder = new CriteriaRecorder();

        String rule = recorder.render(StartupSpecifications.isRaisingAsSeenBy(true, null));

        assertThat(rule).isEqualTo("and(isTrue(isRaising), isTrue(fundraisingVisible))");
        assertThat(recorder.calls()).noneMatch(c -> c.startsWith("subquery"));
    }

    @Test
    void raisingForASignedInViewerAlsoAllowsTheirOwnActiveTeamStartups() {
        CriteriaRecorder recorder = new CriteriaRecorder();

        String rule = recorder.render(StartupSpecifications.isRaisingAsSeenBy(true, "viewer-1"));

        // stored flag AND (fundraising visible OR the viewer is an ACTIVE member of the startup)
        assertThat(rule).startsWith("and(isTrue(isRaising), or(isTrue(fundraisingVisible), in(subquery(String))))");
        assertThat(recorder.calls())
                .contains("from(StartupTeamMember)", "equal(userId, viewer-1)", "equal(status, ACTIVE)");
    }

    @Test
    void notRaisingIsTheExactComplementSoAHiddenStartupIsNotRevealedByItsAbsence() {
        CriteriaRecorder recorder = new CriteriaRecorder();

        String anonymous = recorder.render(StartupSpecifications.isRaisingAsSeenBy(false, null));
        String signedIn = new CriteriaRecorder().render(StartupSpecifications.isRaisingAsSeenBy(false, "viewer-1"));

        assertThat(anonymous).isEqualTo("not(and(isTrue(isRaising), isTrue(fundraisingVisible)))");
        assertThat(signedIn).startsWith("not(and(isTrue(isRaising), or(isTrue(fundraisingVisible), in(");
    }

    @Test
    void theAdminListingKeepsTheRawStoredFlag() {
        CriteriaRecorder recorder = new CriteriaRecorder();

        String rule = recorder.render(StartupSpecifications.isRaising(true));

        assertThat(rule).isEqualTo("equal(isRaising, true)");
        assertThat(StartupSpecifications.isRaising(null)).isNull();
    }
}
