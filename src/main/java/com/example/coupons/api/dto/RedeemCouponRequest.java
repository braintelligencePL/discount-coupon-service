package com.example.coupons.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedeemCouponRequest(@NotBlank @Size(max = 200) String userId) {
}
