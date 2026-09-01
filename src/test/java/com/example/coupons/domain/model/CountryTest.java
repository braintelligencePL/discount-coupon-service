package com.example.coupons.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coupons.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

class CountryTest {

    @Test
    void normalizes_a_valid_alpha2_code_to_uppercase() {
        assertThat(Country.of(" pl ").value()).isEqualTo("PL");
        assertThat(new Country("DE").value()).isEqualTo("DE");
    }

    @Test
    void rejects_unknown_or_malformed_codes() {
        assertThatThrownBy(() -> Country.of("XX")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> Country.of("PLX")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> Country.of(null)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new Country("de")).isInstanceOf(DomainValidationException.class);
    }
}
