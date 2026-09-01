package com.example.coupons.domain.model;

import com.example.coupons.domain.exception.DomainValidationException;
import java.time.Clock;
import java.time.Instant;

public record CouponRedemption(
        CouponCode couponCode,
        String userId,
        Instant redeemedAt,
        Country resolvedCountry) {

    public CouponRedemption {
        if (couponCode == null) {
            throw new DomainValidationException("Redemption requires a coupon code");
        }
        if (userId == null || userId.isBlank()) {
            throw new DomainValidationException("Redemption requires a non-blank user id");
        }
        if (redeemedAt == null) {
            throw new DomainValidationException("Redemption requires a timestamp");
        }
        if (resolvedCountry == null) {
            throw new DomainValidationException("Redemption requires a resolved country");
        }
    }

    public static CouponRedemption record(CouponCode couponCode, String userId, Country resolvedCountry, Clock clock) {
        return new CouponRedemption(couponCode, userId, clock.instant(), resolvedCountry);
    }
}
