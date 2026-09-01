package com.example.coupons.domain.exception;

import com.example.coupons.domain.model.CouponCode;

public class AlreadyRedeemedException extends RuntimeException {

    public AlreadyRedeemedException(CouponCode couponCode, String userId) {
        super("User '" + userId + "' has already redeemed coupon " + couponCode.value());
    }
}
