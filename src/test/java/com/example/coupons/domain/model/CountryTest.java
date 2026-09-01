package com.example.coupons.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coupons.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CountryTest {

    @Test
    @DisplayName("should trim and uppercase the code")
    void should_trim_and_uppercase_the_code() {
        // then
        assertThat(Country.of(" pl ").value()).isEqualTo("PL");
    }

    @Test
    @DisplayName("should accept an already uppercase code in the canonical constructor")
    void should_accept_an_already_uppercase_code_in_the_canonical_constructor() {
        // then
        assertThat(new Country("DE").value()).isEqualTo("DE");
    }

    @Test
    @DisplayName("should reject an unknown code")
    void should_reject_an_unknown_code() {
        // then
        assertThatThrownBy(() -> Country.of("XX")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a malformed code")
    void should_reject_a_malformed_code() {
        // then
        assertThatThrownBy(() -> Country.of("PLX")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a null code")
    void should_reject_a_null_code() {
        // then
        assertThatThrownBy(() -> Country.of(null)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a lowercase value in the canonical constructor")
    void should_reject_a_lowercase_value_in_the_canonical_constructor() {
        // then
        assertThatThrownBy(() -> new Country("de")).isInstanceOf(DomainValidationException.class);
    }
}
