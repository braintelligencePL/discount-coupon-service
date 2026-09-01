package com.example.coupons.domain.model;

import com.example.coupons.domain.exception.DomainValidationException;
import java.time.Clock;
import java.time.Instant;

public record Coupon(
        Long id,
        CouponCode code,
        Instant createdAt,
        UsageLimit maxUses,
        int currentUses,
        Country country) {

    public Coupon {
        if (code == null) {
            throw new DomainValidationException("Coupon code is required");
        }
        if (createdAt == null) {
            throw new DomainValidationException("Coupon creation instant is required");
        }
        if (maxUses == null) {
            throw new DomainValidationException("Coupon maximum uses is required");
        }
        if (country == null) {
            throw new DomainValidationException("Coupon target country is required");
        }
        if (currentUses < 0) {
            throw new DomainValidationException("Current uses must not be negative, was " + currentUses);
        }
        if (currentUses > maxUses.value()) {
            throw new DomainValidationException(
                    "Current uses (" + currentUses + ") must not exceed maximum uses (" + maxUses.value() + ")");
        }
    }

    public static Coupon create(String rawCode, int maxUses, String rawCountry, Clock clock) {
        return new Coupon(null, CouponCode.of(rawCode), clock.instant(),
                UsageLimit.of(maxUses), 0, Country.of(rawCountry));
    }

    public boolean isExhausted() {
        return currentUses >= maxUses.value();
    }

    public int remainingUses() {
        return maxUses.value() - currentUses;
    }
}
