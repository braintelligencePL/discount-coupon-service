package com.example.coupons.api.web;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.coupons.application.CouponService;
import com.example.coupons.domain.exception.DuplicateCouponCodeException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.Country;
import com.example.coupons.domain.model.UsageLimit;
import com.example.coupons.infrastructure.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CouponController.class)
@Import(ApiExceptionHandler.class)
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CouponService couponService;

    private static Coupon sampleCoupon() {
        return new Coupon(1L, CouponCode.of("summer"), Instant.parse("2026-01-01T00:00:00Z"),
                UsageLimit.of(3), 0, Country.of("PL"));
    }

    @Test
    void create_returns_201_with_location_and_body() throws Exception {
        when(couponService.create(any())).thenReturn(sampleCoupon());

        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SUMMER\",\"maxUses\":3,\"country\":\"PL\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/coupons/summer")))
                .andExpect(jsonPath("$.code").value("summer"))
                .andExpect(jsonPath("$.maxUses").value(3))
                .andExpect(jsonPath("$.currentUses").value(0))
                .andExpect(jsonPath("$.remainingUses").value(3))
                .andExpect(jsonPath("$.country").value("PL"));
    }

    @Test
    void create_with_duplicate_code_returns_409_problem_json() throws Exception {
        when(couponService.create(any())).thenThrow(new DuplicateCouponCodeException("summer"));

        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SUMMER\",\"maxUses\":3,\"country\":\"PL\"}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Coupon code already exists"))
                .andExpect(jsonPath("$.instance").value("/api/v1/coupons"));
    }

    @Test
    void create_with_an_invalid_body_returns_400_validation_error_with_field_details() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"  \"}")) // blank code + missing maxUses
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void malformed_json_body_returns_400_problem_json_with_code() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
