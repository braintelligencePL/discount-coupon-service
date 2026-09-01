package com.example.coupons.domain.exception;

public class CouponNotFoundException extends RuntimeException {

    public CouponNotFoundException(String code) {
        super("No coupon found for code '" + code + "'");
    }
}
