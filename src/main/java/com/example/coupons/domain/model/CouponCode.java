package com.example.coupons.domain.model;

import com.example.coupons.domain.exception.DomainValidationException;
import java.util.Locale;

public record CouponCode(String value) {

    public static final int MAX_LENGTH = 64;

    public CouponCode {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("Coupon code must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException(
                    "Coupon code must be at most " + MAX_LENGTH + " characters, was " + value.length());
        }
        if (!value.equals(normalize(value))) {
            throw new DomainValidationException("Coupon code must be normalized (trimmed and lower-case)");
        }
    }

    public static CouponCode of(String raw) {
        if (raw == null) {
            throw new DomainValidationException("Coupon code must not be null");
        }
        return new CouponCode(normalize(raw));
    }

    private static String normalize(String raw) {
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }
}
