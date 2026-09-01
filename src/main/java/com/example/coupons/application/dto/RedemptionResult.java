package com.example.coupons.application.dto;

import java.time.Instant;

public record RedemptionResult(String code, int remainingUses, String resolvedCountry, Instant redeemedAt) {
}
