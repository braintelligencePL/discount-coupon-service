package com.example.coupons.api.web;

import com.example.coupons.api.dto.CouponResponse;
import com.example.coupons.api.dto.CreateCouponRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Coupons", description = "Create and look up discount coupons")
interface CouponApi {

    @Operation(summary = "Create a coupon")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Coupon created"),
            @ApiResponse(responseCode = "400", description = "Validation error (application/problem+json)"),
            @ApiResponse(responseCode = "409",
                    description = "A coupon with this code already exists (application/problem+json)")
    })
    ResponseEntity<CouponResponse> create(CreateCouponRequest request, UriComponentsBuilder uriBuilder);

    @Operation(summary = "Look up a coupon by code (case-insensitive)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coupon found"),
            @ApiResponse(responseCode = "404",
                    description = "No coupon with this code (application/problem+json)")
    })
    ResponseEntity<CouponResponse> getByCode(String code);
}
