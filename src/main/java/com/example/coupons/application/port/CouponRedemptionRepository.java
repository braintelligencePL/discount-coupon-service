package com.example.coupons.application.port;

import com.example.coupons.domain.exception.AlreadyRedeemedException;
import com.example.coupons.domain.model.CouponRedemption;


public interface CouponRedemptionRepository {
    void insert(CouponRedemption redemption);
}
