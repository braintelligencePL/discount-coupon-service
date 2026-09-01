package com.example.coupons.domain.exception;

public class DuplicateCouponCodeException extends RuntimeException {

    public DuplicateCouponCodeException(String code) {
        super("A coupon with code '" + code + "' already exists");
    }
}
