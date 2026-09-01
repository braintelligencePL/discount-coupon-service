package com.example.coupons.domain.exception;


public class AlreadyRedeemedException extends RuntimeException {

    public AlreadyRedeemedException(Long couponId, String userId) {
        super("User '" + userId + "' has already redeemed coupon " + couponId);
    }
}
