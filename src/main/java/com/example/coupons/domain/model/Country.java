package com.example.coupons.domain.model;

import com.example.coupons.domain.exception.DomainValidationException;
import java.util.Locale;
import java.util.Set;

public record Country(String value) {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    public Country {
        if (value == null || !ISO_COUNTRIES.contains(value)) {
            throw new DomainValidationException("Unknown ISO 3166-1 alpha-2 country code: " + value);
        }
    }

    public static Country of(String raw) {
        if (raw == null) {
            throw new DomainValidationException("Country code must not be null");
        }
        return new Country(raw.strip().toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
