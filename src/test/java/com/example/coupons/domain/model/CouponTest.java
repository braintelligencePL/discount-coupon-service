package com.example.coupons.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coupons.domain.exception.DomainValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CouponTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("should normalize the code, stamp the time and zero the counter on create")
    void should_normalize_code_stamp_time_and_zero_the_counter_on_create() {
        // when
        Coupon coupon = Coupon.create("  SUMMER ", 3, "de", FIXED_CLOCK);

        // then
        assertThat(coupon.code()).isEqualTo(CouponCode.of("summer"));
        assertThat(coupon.createdAt()).isEqualTo(NOW);
        assertThat(coupon.maxUses()).isEqualTo(UsageLimit.of(3));
        assertThat(coupon.currentUses()).isZero();
        assertThat(coupon.country()).isEqualTo(Country.of("DE"));
    }

    @Test
    @DisplayName("should reject a blank country on create")
    void should_reject_a_blank_country_on_create() {
        // then
        assertThatThrownBy(() -> Coupon.create("x", 1, "   ", FIXED_CLOCK))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should report uses remaining when the counter is below the limit")
    void should_report_uses_remaining_when_the_counter_is_below_the_limit() {
        // given
        Coupon fresh = new Coupon(CouponCode.of("a"), NOW, UsageLimit.of(2), 0, Country.of("PL"));

        // then
        assertThat(fresh.isExhausted()).isFalse();
        assertThat(fresh.remainingUses()).isEqualTo(2);
    }

    @Test
    @DisplayName("should report exhausted when the counter reaches the limit")
    void should_report_exhausted_when_the_counter_reaches_the_limit() {
        // given
        Coupon exhausted = new Coupon(CouponCode.of("a"), NOW, UsageLimit.of(2), 2, Country.of("PL"));

        // then
        assertThat(exhausted.isExhausted()).isTrue();
        assertThat(exhausted.remainingUses()).isZero();
    }

    @Test
    @DisplayName("should reject a null code")
    void should_reject_a_null_code() {
        // then
        assertThatThrownBy(() -> new Coupon(null, NOW, UsageLimit.of(1), 0, Country.of("PL")))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a null created-at timestamp")
    void should_reject_a_null_created_at() {
        // then
        assertThatThrownBy(() -> new Coupon(CouponCode.of("a"), null, UsageLimit.of(1), 0, Country.of("PL")))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a null country")
    void should_reject_a_null_country() {
        // then
        assertThatThrownBy(() -> new Coupon(CouponCode.of("a"), NOW, UsageLimit.of(1), 0, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a negative use counter")
    void should_reject_a_negative_use_counter() {
        // then
        assertThatThrownBy(() -> new Coupon(CouponCode.of("a"), NOW, UsageLimit.of(2), -1, Country.of("PL")))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a use counter above the limit")
    void should_reject_a_use_counter_above_the_limit() {
        // then
        assertThatThrownBy(() -> new Coupon(CouponCode.of("a"), NOW, UsageLimit.of(2), 3, Country.of("PL")))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a non-positive max-uses via the factory")
    void should_reject_a_non_positive_max_uses_via_the_factory() {
        // then
        assertThatThrownBy(() -> Coupon.create("valid", 0, "PL", FIXED_CLOCK))
                .isInstanceOf(DomainValidationException.class);
    }
}
