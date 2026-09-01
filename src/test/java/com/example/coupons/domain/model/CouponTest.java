package com.example.coupons.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coupons.domain.exception.DomainValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CouponTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void create_normalizes_stamps_time_zeroes_the_counter_and_handles_country() {
        Coupon coupon = Coupon.create("  SUMMER ", 3, "de", FIXED_CLOCK);

        assertThat(coupon.id()).isNull();
        assertThat(coupon.code()).isEqualTo(CouponCode.of("summer"));
        assertThat(coupon.createdAt()).isEqualTo(NOW);
        assertThat(coupon.maxUses()).isEqualTo(UsageLimit.of(3));
        assertThat(coupon.currentUses()).isZero();
        assertThat(coupon.country()).isEqualTo(Country.of("DE"));

        assertThatThrownBy(() -> Coupon.create("x", 1, "   ", FIXED_CLOCK))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void query_methods_reflect_counter_state() {
        Coupon fresh = new Coupon(1L, CouponCode.of("a"), NOW, UsageLimit.of(2), 0, Country.of("PL"));
        assertThat(fresh.isExhausted()).isFalse();
        assertThat(fresh.remainingUses()).isEqualTo(2);

        Coupon exhausted = new Coupon(1L, CouponCode.of("a"), NOW, UsageLimit.of(2), 2, Country.of("PL"));
        assertThat(exhausted.isExhausted()).isTrue();
        assertThat(exhausted.remainingUses()).isZero();
    }

    @Test
    void rejects_invalid_state() {
        // missing required fields
        assertThatThrownBy(() -> new Coupon(null, null, NOW, UsageLimit.of(1), 0, Country.of("PL")))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new Coupon(null, CouponCode.of("a"), null, UsageLimit.of(1), 0, Country.of("PL")))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new Coupon(null, CouponCode.of("a"), NOW, UsageLimit.of(1), 0, null))
                .isInstanceOf(DomainValidationException.class);
        // counter out of range
        assertThatThrownBy(() -> new Coupon(1L, CouponCode.of("a"), NOW, UsageLimit.of(2), -1, Country.of("PL")))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new Coupon(1L, CouponCode.of("a"), NOW, UsageLimit.of(2), 3, Country.of("PL")))
                .isInstanceOf(DomainValidationException.class);
        // factory propagates value-object validation
        assertThatThrownBy(() -> Coupon.create("valid", 0, "PL", FIXED_CLOCK))
                .isInstanceOf(DomainValidationException.class);
    }
}
