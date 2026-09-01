package com.example.coupons.domain.exception;

public class CountryNotDeterminedException extends RuntimeException {

    public CountryNotDeterminedException(String code) {
        super("Could not determine the caller's country; coupon '" + code + "' is country-restricted");
    }
}
