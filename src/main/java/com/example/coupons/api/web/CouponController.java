package com.example.coupons.api.web;

import com.example.coupons.api.dto.CouponResponse;
import com.example.coupons.api.dto.CreateCouponRequest;
import com.example.coupons.api.web.support.ApiRoutes;
import com.example.coupons.application.CouponService;
import com.example.coupons.application.dto.CreateCoupon;
import com.example.coupons.domain.model.Coupon;
import jakarta.validation.Valid;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping(ApiRoutes.COUPONS)
class CouponController implements CouponApi {

    private final CouponService couponService;

    CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @Override
    @PostMapping
    public ResponseEntity<CouponResponse> create(
            @Valid @RequestBody CreateCouponRequest request,
            UriComponentsBuilder uriBuilder) {

        Coupon coupon = couponService.create(
                new CreateCoupon(
                        request.code(),
                        request.maxUses(),
                        request.country()
                ));

        URI location = uriBuilder.path(ApiRoutes.COUPON_BY_CODE)
                .buildAndExpand(coupon.code().value()).toUri();

        return ResponseEntity.created(location)
                .contentType(MediaType.APPLICATION_JSON)
                .body(CouponResponse.from(coupon));
    }

    @Override
    @GetMapping("/{code}")
    public ResponseEntity<CouponResponse> getByCode(
            @PathVariable String code) {

        return ResponseEntity.ok(CouponResponse.from(couponService.getByCode(code)));
    }
}
