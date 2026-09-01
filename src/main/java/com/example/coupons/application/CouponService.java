package com.example.coupons.application;

import com.example.coupons.domain.exception.CountryNotAllowedException;
import com.example.coupons.domain.exception.CountryNotDeterminedException;
import com.example.coupons.domain.exception.CouponNotFoundException;
import com.example.coupons.domain.exception.DuplicateCouponCodeException;
import com.example.coupons.domain.exception.UsageLimitReachedException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.CouponRedemption;
import com.example.coupons.domain.model.Country;
import com.example.coupons.application.dto.CreateCoupon;
import com.example.coupons.application.dto.RedeemCoupon;
import com.example.coupons.application.dto.RedemptionResult;
import com.example.coupons.application.port.CouponRedemptionRepository;
import com.example.coupons.application.port.CouponRepository;
import com.example.coupons.application.port.GeoIpResolver;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final GeoIpResolver geoIpResolver;
    private final Clock clock;

    public Coupon create(CreateCoupon createCoupon) {
        Coupon coupon = Coupon.create(createCoupon.code(), createCoupon.maxUses(), createCoupon.country(), clock);
        return couponRepository.save(coupon);
    }

    public Coupon getByCode(String rawCode) {
        CouponCode code = CouponCode.of(rawCode);
        return couponRepository.findByCode(code)
                .orElseThrow(() -> new CouponNotFoundException(code.value()));
    }

    @Transactional
    public RedemptionResult redeem(RedeemCoupon redeemCoupon) {
        CouponCode code = CouponCode.of(redeemCoupon.code());
        log.info("redemption requested code={} user={}", code.value(), redeemCoupon.userId());

        try {
            Coupon coupon = couponRepository.findByCode(code)
                    .orElseThrow(() -> new CouponNotFoundException(code.value()));

            Country resolvedCountry = checkCountryRestriction(coupon, redeemCoupon.callerIp());
            RedemptionResult result = registerRedemption(coupon, redeemCoupon.userId(), resolvedCountry);

            log.info("redemption outcome=SUCCESS code={} user={} country={} remainingUses={}",
                    code.value(), redeemCoupon.userId(), result.resolvedCountry(), result.remainingUses());

            return result;
        } catch (RuntimeException ex) {
            log.info("redemption outcome={} code={} user={}",
                    ex.getClass().getSimpleName(), code.value(), redeemCoupon.userId());
            throw ex;
        }
    }

    private Country checkCountryRestriction(Coupon coupon, String callerIp) {
        Country caller = geoIpResolver.resolve(callerIp)
                .orElseThrow(() -> new CountryNotDeterminedException(coupon.code().value()));
        if (!caller.equals(coupon.country())) {
            throw new CountryNotAllowedException(coupon.code().value(), caller.value());
        }
        return caller;
    }

    private RedemptionResult registerRedemption(Coupon coupon, String userId, Country resolvedCountry) {
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
