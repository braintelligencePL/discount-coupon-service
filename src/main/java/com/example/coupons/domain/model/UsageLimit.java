package com.example.coupons.domain.model;

import com.example.coupons.domain.exception.DomainValidationException;

public record UsageLimit(int value) {

    public UsageLimit {
        if (value <= 0) {
            throw new DomainValidationException("Maximum uses must be a positive number, was " + value);
        }
    }

    public static UsageLimit of(int value) {
        return new UsageLimit(value);
    }
}
