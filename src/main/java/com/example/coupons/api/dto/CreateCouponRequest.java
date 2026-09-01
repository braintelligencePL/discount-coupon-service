package com.example.coupons.api.dto;

import com.example.coupons.domain.model.CouponCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCouponRequest(
        @NotBlank @Size(max = CouponCode.MAX_LENGTH) String code,
        @NotNull @Positive Integer maxUses,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166-1 alpha-2 code") String country) {
}
