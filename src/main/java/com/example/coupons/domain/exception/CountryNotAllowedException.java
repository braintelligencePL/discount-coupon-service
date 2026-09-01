package com.example.coupons.domain.exception;

public class CountryNotAllowedException extends RuntimeException {

    public CountryNotAllowedException(String code, String callerCountry) {
        super("Coupon '" + code + "' is not available from country " + callerCountry);
    }
}
