package com.nukkad.common.validation;

import com.nukkad.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkSanitizerTest {

    @Test
    void blankOrNullInputIsNull() {
        assertThat(LinkSanitizer.normalizeHttpUrl(null, "Website")).isNull();
        assertThat(LinkSanitizer.normalizeHttpUrl("", "Website")).isNull();
        assertThat(LinkSanitizer.normalizeHttpUrl("   ", "Website")).isNull();
    }

    @Test
    void anAlreadyAbsoluteHttpsUrlIsKeptAsIs() {
        assertThat(LinkSanitizer.normalizeHttpUrl("https://github.com/me", "GitHub URL")).isEqualTo("https://github.com/me");
    }

    @Test
    void anAlreadyAbsoluteHttpUrlIsKeptAsIs() {
        assertThat(LinkSanitizer.normalizeHttpUrl("http://example.com", "Website")).isEqualTo("http://example.com");
    }

    @Test
    void aBareDomainGetsHttpsPutInFront() {
        // None of these fields' forms normalize the value before submitting (unlike the Feed
        // composer's link field, which does) — a bare "github.com/me" must still work.
        assertThat(LinkSanitizer.normalizeHttpUrl("github.com/me", "GitHub URL")).isEqualTo("https://github.com/me");
    }

    @Test
    void aJavascriptUriIsRejectedOutrightNeverAutoCorrected() {
        // The forced https:// prefix (added because the input has no recognized scheme) makes this
        // structurally impossible to end up scheme=javascript, but assert the actual behavior anyway:
        // it's rejected, not silently coerced into some other unintended value.
        assertThatThrownBy(() -> LinkSanitizer.normalizeHttpUrl("javascript:alert(1)", "Credential URL"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aDataUriIsRejected() {
        assertThatThrownBy(() -> LinkSanitizer.normalizeHttpUrl("data:text/html,<script>alert(1)</script>", "Publication URL"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void malformedInputIsRejected() {
        assertThatThrownBy(() -> LinkSanitizer.normalizeHttpUrl("https://", "Website"))
                .isInstanceOf(BadRequestException.class);
    }
}
