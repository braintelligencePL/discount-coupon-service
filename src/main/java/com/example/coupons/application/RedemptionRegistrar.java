package com.example.coupons.application;

import com.example.coupons.application.dto.RedemptionResult;
import com.example.coupons.application.port.CouponRedemptionRepository;
import com.example.coupons.application.port.CouponRepository;
import com.example.coupons.domain.exception.UsageLimitReachedException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponRedemption;
import com.example.coupons.domain.model.Country;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class RedemptionRegistrar {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final Clock clock;

    @Transactional
    public RedemptionResult register(Coupon coupon, String userId, Country resolvedCountry) {
        CouponRedemption redemption = CouponRedemption.record(coupon.code(), userId, resolvedCountry, clock);
        redemptionRepository.insert(redemption);

        if (couponRepository.incrementUsageIfBelowLimit(coupon.code()) == 0) {
            throw new UsageLimitReachedException(coupon.code().value());
        }

        Coupon updated = couponRepository.findByCode(coupon.code()).orElseThrow();
        return new RedemptionResult(updated.code().value(), updated.remainingUses(),
                resolvedCountry.value(), redemption.redeemedAt());
    }
}
