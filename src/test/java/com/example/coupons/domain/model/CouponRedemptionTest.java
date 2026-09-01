package com.example.coupons.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coupons.domain.exception.DomainValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CouponRedemptionTest {

    private static final Instant NOW = Instant.parse("2026-05-06T07:08:09Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("should stamp the time and the resolved country on record")
    void should_stamp_the_time_and_resolved_country_on_record() {
        // when
        CouponRedemption r = CouponRedemption.record(CouponCode.of("wiosna"), "user-1", Country.of("PL"), FIXED_CLOCK);

        // then
        assertThat(r.couponCode()).isEqualTo(CouponCode.of("wiosna"));
        assertThat(r.userId()).isEqualTo("user-1");
        assertThat(r.redeemedAt()).isEqualTo(NOW);
        assertThat(r.resolvedCountry()).isEqualTo(Country.of("PL"));
    }

    @Test
    @DisplayName("should reject a null coupon code")
    void should_reject_a_null_coupon_code() {
        // then
        assertThatThrownBy(() -> CouponRedemption.record(null, "u", Country.of("PL"), FIXED_CLOCK))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a blank user id")
    void should_reject_a_blank_user_id() {
        // then
        assertThatThrownBy(() -> CouponRedemption.record(CouponCode.of("x"), "  ", Country.of("PL"), FIXED_CLOCK))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a null resolved country")
    void should_reject_a_null_resolved_country() {
        // then
        assertThatThrownBy(() -> CouponRedemption.record(CouponCode.of("x"), "u", null, FIXED_CLOCK))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a null required field in the canonical constructor")
    void should_reject_a_null_required_field_in_the_canonical_constructor() {
        // then
        assertThatThrownBy(() -> new CouponRedemption(CouponCode.of("x"), "u", null, null))
                .isInstanceOf(DomainValidationException.class);
    }
}
