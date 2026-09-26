package com.nukkad.common.publishing;

import com.nukkad.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** This enum is shared across every content type an admin can create — Feed's own backfill (V112)
 *  and every admin-create form's dropdown hardcode this exact five-identity list elsewhere, so this
 *  pins the enum itself against silent desync. */
class PublisherIdentityTest {

    @Test
    void hasExactlyTheFiveAuthorizedIdentitiesInDeclarationOrder() {
        List<String> names = Arrays.stream(PublisherIdentity.values()).map(Enum::name).toList();

        assertThat(names).containsExactly("BUILDADDA", "ARJUN_MEHTA", "KARAN_SHAH", "NEEL_KAPOOR", "VIKRAM_RAO");
    }

    @Test
    void everyIdentityHasItsFullDisplayName() {
        assertThat(PublisherIdentity.BUILDADDA.getLabel()).isEqualTo("BuildAdda");
        assertThat(PublisherIdentity.ARJUN_MEHTA.getLabel()).isEqualTo("Arjun Mehta");
        assertThat(PublisherIdentity.KARAN_SHAH.getLabel()).isEqualTo("Karan Shah");
        assertThat(PublisherIdentity.NEEL_KAPOOR.getLabel()).isEqualTo("Neel Kapoor");
        assertThat(PublisherIdentity.VIKRAM_RAO.getLabel()).isEqualTo("Vikram Rao");
    }

    @Test
    void parseIsCaseInsensitiveAndTrims() {
        assertThat(PublisherIdentity.parse("  karan_shah  ", PublisherIdentity.BUILDADDA)).isEqualTo(PublisherIdentity.KARAN_SHAH);
        assertThat(PublisherIdentity.parse("NEEL_KAPOOR", PublisherIdentity.BUILDADDA)).isEqualTo(PublisherIdentity.NEEL_KAPOOR);
    }

    @Test
    void parseFallsBackForBlankOrNull() {
        assertThat(PublisherIdentity.parse(null, PublisherIdentity.VIKRAM_RAO)).isEqualTo(PublisherIdentity.VIKRAM_RAO);
        assertThat(PublisherIdentity.parse("   ", PublisherIdentity.VIKRAM_RAO)).isEqualTo(PublisherIdentity.VIKRAM_RAO);
    }

    @Test
    void parseRejectsAnUnknownValueRatherThanCoercingIt() {
        assertThatThrownBy(() -> PublisherIdentity.parse("someone_else", PublisherIdentity.BUILDADDA))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("someone_else");
    }
}
