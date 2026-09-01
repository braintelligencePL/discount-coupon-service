package com.example.coupons.api.web.support;

public final class ApiRoutes {

    public static final String COUPONS = "/api/v1/coupons";
    public static final String COUPON_BY_CODE = COUPONS + "/{code}";
    public static final String REDEMPTIONS = COUPON_BY_CODE + "/redemptions";

    private ApiRoutes() {}
}
