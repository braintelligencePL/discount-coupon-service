package com.example.coupons.api.dto;

import com.example.coupons.application.dto.RedemptionResult;
import java.time.Instant;

public record RedemptionResponse(
        String code,
        String userId,
        int remainingUses,
        String resolvedCountry,
        Instant redeemedAt) {

    public static RedemptionResponse from(RedemptionResult result, String userId) {
        return new RedemptionResponse(
                result.code(), userId, result.remainingUses(), result.resolvedCountry(), result.redeemedAt());
    }
}
