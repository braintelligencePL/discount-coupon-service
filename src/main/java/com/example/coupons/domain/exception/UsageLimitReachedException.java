package com.example.coupons.domain.exception;

public class UsageLimitReachedException extends RuntimeException {

    public UsageLimitReachedException(String code) {
        super("Coupon '" + code + "' has reached its usage limit");
    }
}
