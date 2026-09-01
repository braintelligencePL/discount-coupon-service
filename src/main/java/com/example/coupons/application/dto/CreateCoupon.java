package com.example.coupons.application.dto;

public record CreateCoupon(String code, int maxUses, String country) {
}
