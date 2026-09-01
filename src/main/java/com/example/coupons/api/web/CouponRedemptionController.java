package com.example.coupons.api.web;

import com.example.coupons.api.dto.RedeemCouponRequest;
import com.example.coupons.api.dto.RedemptionResponse;
import com.example.coupons.api.web.support.ApiRoutes;
import com.example.coupons.application.CouponService;
import com.example.coupons.application.dto.RedeemCoupon;
import com.example.coupons.application.dto.RedemptionResult;
import com.example.coupons.infrastructure.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiRoutes.REDEMPTIONS)
class CouponRedemptionController implements CouponRedemptionApi {

    private final CouponService couponService;
    private final ClientIpResolver clientIpResolver;

    CouponRedemptionController(CouponService couponService, ClientIpResolver clientIpResolver) {
        this.couponService = couponService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    @PostMapping
    public ResponseEntity<RedemptionResponse> redeem(@PathVariable String code,
                                                     @Valid @RequestBody RedeemCouponRequest request,
                                                     HttpServletRequest httpRequest) {
        RedemptionResult result = couponService.redeem(
                new RedeemCoupon(code, request.userId(), clientIpResolver.resolve(httpRequest)));

        return ResponseEntity.ok(
                RedemptionResponse.from(result, request.userId())
        );
    }
}
