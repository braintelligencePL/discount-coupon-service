package com.example.coupons.api.dto;

import com.example.coupons.domain.model.Coupon;
import java.time.Instant;

public record CouponResponse(
        String code,
        Instant createdAt,
        int maxUses,
        int currentUses,
        int remainingUses,
        String country) {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.code().value(),
                coupon.createdAt(),
                coupon.maxUses().value(),
                coupon.currentUses(),
                coupon.remainingUses(),
                coupon.country().value());
    }
}
