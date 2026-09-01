package com.example.coupons.api.web;

import com.example.coupons.api.dto.RedeemCouponRequest;
import com.example.coupons.api.dto.RedemptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "Redemptions", description = "Register a coupon redemption by a user")
interface CouponRedemptionApi {

    @Operation(summary = "Register a redemption of this coupon by a user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Redemption registered"),
            @ApiResponse(responseCode = "400", description = "Validation error (application/problem+json)"),
            @ApiResponse(responseCode = "403",
                    description = "Coupon not available from the caller's country (application/problem+json)"),
            @ApiResponse(responseCode = "404", description = "No coupon with this code (application/problem+json)"),
            @ApiResponse(responseCode = "409",
                    description = "Usage limit reached, or user already redeemed this coupon "
                            + "(application/problem+json)"),
            @ApiResponse(responseCode = "422",
                    description = "The caller's country could not be determined (application/problem+json)")
    })
    ResponseEntity<RedemptionResponse> redeem(
            String code, RedeemCouponRequest request, HttpServletRequest httpRequest);
}
