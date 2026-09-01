package com.example.coupons.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coupons.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsageLimitTest {

    @Test
    @DisplayName("should accept a positive value")
    void should_accept_a_positive_value() {
        // then
        assertThat(UsageLimit.of(1).value()).isEqualTo(1);
    }

    @Test
    @DisplayName("should reject zero")
    void should_reject_zero() {
        // then
        assertThatThrownBy(() -> UsageLimit.of(0)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("should reject a negative value")
    void should_reject_a_negative_value() {
        // then
        assertThatThrownBy(() -> UsageLimit.of(-1)).isInstanceOf(DomainValidationException.class);
    }
}
