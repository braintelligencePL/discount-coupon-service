package com.example.coupons.application.port;

import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.exception.DuplicateCouponCodeException;
import java.util.Optional;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findByCode(CouponCode code);

    int incrementUsageIfBelowLimit(CouponCode code);

}
