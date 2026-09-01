package com.example.coupons.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coupons.domain.exception.DomainValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CouponRedemptionTest {

    private static final Instant NOW = Instant.parse("2026-05-06T07:08:09Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void record_stamps_the_time_and_the_resolved_country() {
        CouponRedemption r = CouponRedemption.record(1L, "user-1", Country.of("PL"), FIXED_CLOCK);

        assertThat(r.couponId()).isEqualTo(1L);
        assertThat(r.userId()).isEqualTo("user-1");
        assertThat(r.redeemedAt()).isEqualTo(NOW);
        assertThat(r.resolvedCountry()).isEqualTo(Country.of("PL"));
    }

    @Test
    void rejects_missing_required_fields() {
        assertThatThrownBy(() -> CouponRedemption.record(null, "u", Country.of("PL"), FIXED_CLOCK))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> CouponRedemption.record(1L, "  ", Country.of("PL"), FIXED_CLOCK))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> CouponRedemption.record(1L, "u", null, FIXED_CLOCK))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new CouponRedemption(1L, "u", null, null))
                .isInstanceOf(DomainValidationException.class);
    }
}
