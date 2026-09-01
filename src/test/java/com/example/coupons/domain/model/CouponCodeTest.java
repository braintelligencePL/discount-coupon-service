package com.example.coupons.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coupons.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

class CouponCodeTest {

    @Test
    void normalizes_and_is_case_insensitive() {
        assertThat(CouponCode.of("  WIOSNA  ").value()).isEqualTo("wiosna");
        assertThat(CouponCode.of("WIOSNA")).isEqualTo(CouponCode.of("wiosna"));
        assertThatThrownBy(() -> new CouponCode("WIOSNA")) // canonical form must already be normalized
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejects_blank_null_and_oversized() {
        assertThatThrownBy(() -> CouponCode.of("   ")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> CouponCode.of(null)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> CouponCode.of("a".repeat(CouponCode.MAX_LENGTH + 1)))
                .isInstanceOf(DomainValidationException.class);
        assertThat(CouponCode.of("a".repeat(CouponCode.MAX_LENGTH)).value()).hasSize(CouponCode.MAX_LENGTH);
    }
}
