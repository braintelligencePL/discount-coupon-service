package com.example.coupons.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coupons.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

class UsageLimitTest {

    @Test
    void accepts_a_positive_value_and_rejects_anything_else() {
        assertThat(UsageLimit.of(1).value()).isEqualTo(1);
        assertThatThrownBy(() -> UsageLimit.of(0)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> UsageLimit.of(-1)).isInstanceOf(DomainValidationException.class);
    }
}
