package com.example.coupons.domain.model;

import com.example.coupons.domain.exception.DomainValidationException;
import java.time.Clock;
import java.time.Instant;

public record CouponRedemption(
        Long couponId,
        String userId,
        Instant redeemedAt,
        Country resolvedCountry) {

    public CouponRedemption {
        if (couponId == null) {
            throw new DomainValidationException("Redemption requires a coupon id");
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

    public static CouponRedemption record(Long couponId, String userId, Country resolvedCountry, Clock clock) {
        return new CouponRedemption(couponId, userId, clock.instant(), resolvedCountry);
    }
}
