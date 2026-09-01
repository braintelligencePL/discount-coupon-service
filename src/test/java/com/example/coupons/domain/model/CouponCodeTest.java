package com.example.coupons.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coupons.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CouponCodeTest {

    @Test
    @DisplayName("should trim and lowercase the value")
    void should_trim_and_lowercase_the_value() {
        // then
        assertThat(CouponCode.of("  WIOSNA  ").value()).isEqualTo("wiosna");
    }

    @Test
    @DisplayName("should treat differently cased codes as equal")
    void should_treat_differently_cased_codes_as_equal() {
        // then
        assertThat(CouponCode.of("WIOSNA")).isEqualTo(CouponCode.of("wiosna"));
    }

    @Test
    @DisplayName("should reject a non-normalized value in the canonical constructor")
    void should_reject_a_non_normalized_value_in_the_canonical_constructor() {
        // then
        // the canonical form must already be normalized
        assertThatThrownBy(() -> new CouponCode("WIOSNA"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a blank code")
    void should_reject_a_blank_code() {
        // then
        assertThatThrownBy(() -> CouponCode.of("   ")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a null code")
    void should_reject_a_null_code() {
        // then
        assertThatThrownBy(() -> CouponCode.of(null)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a code longer than the max")
    void should_reject_a_code_longer_than_the_max() {
        // then
        assertThatThrownBy(() -> CouponCode.of("a".repeat(CouponCode.MAX_LENGTH + 1)))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should accept a code at the max length")
    void should_accept_a_code_at_the_max_length() {
        // then
        assertThat(CouponCode.of("a".repeat(CouponCode.MAX_LENGTH)).value()).hasSize(CouponCode.MAX_LENGTH);
    }
}
