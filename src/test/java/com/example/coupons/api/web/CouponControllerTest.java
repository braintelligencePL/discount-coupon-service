package com.example.coupons.api.web;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.coupons.application.CouponService;
import com.example.coupons.application.dto.CreateCoupon;
import com.example.coupons.domain.exception.CouponNotFoundException;
import com.example.coupons.domain.exception.DuplicateCouponCodeException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.Country;
import com.example.coupons.domain.model.UsageLimit;
import com.example.coupons.infrastructure.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(CouponController.class)
@Import(ApiExceptionHandler.class)
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CouponService couponService;

    @Test
    @DisplayName("should map the request to a command and return 201 with Location and body")
    void should_return_201_with_location_and_body_on_create() throws Exception {
        // given
        when(couponService.create(any())).thenReturn(sampleCoupon());

        // when
        ResultActions result = postCoupon("{\"code\":\"SUMMER\",\"maxUses\":3,\"country\":\"PL\"}");

        // then the request body is mapped to the application command verbatim
        verify(couponService).create(new CreateCoupon("SUMMER", 3, "PL"));

        // and the created coupon comes back as JSON with a Location header
        result.andExpect(status().isCreated())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(header().string("Location", endsWith("/api/v1/coupons/summer")));
        expectSummerCouponBody(result);
    }

    @Test
    @DisplayName("should return 409 problem+json when the code is duplicate")
    void should_return_409_problem_json_when_the_code_is_duplicate() throws Exception {
        // given
        when(couponService.create(any())).thenThrow(new DuplicateCouponCodeException("summer"));

        // when
        ResultActions result = postCoupon("{\"code\":\"SUMMER\",\"maxUses\":3,\"country\":\"PL\"}");

        // then
        result.andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Coupon code already exists"))
                .andExpect(jsonPath("$.instance").value("/api/v1/coupons"));
    }

    @Test
    @DisplayName("should return 400 with field details when the body is invalid")
    void should_return_400_with_field_details_when_the_body_is_invalid() throws Exception {
        // when
        ResultActions result = postCoupon("{\"code\":\"  \"}"); // blank code + missing maxUses

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("should return 400 problem+json when the JSON is malformed")
    void should_return_400_problem_json_when_the_json_is_malformed() throws Exception {
        // when
        ResultActions result = postCoupon("{not valid json");

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("should return 200 with the coupon body when the code exists")
    void should_return_200_with_the_coupon_body_when_the_code_exists() throws Exception {
        // given
        when(couponService.getByCode("summer")).thenReturn(sampleCoupon());

        // when
        ResultActions result = getCoupon("summer");

        // then
        result.andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"));
        expectSummerCouponBody(result);
    }

    @Test
    @DisplayName("should return 404 problem+json when the coupon is missing")
    void should_return_404_problem_json_when_the_coupon_is_missing() throws Exception {
        // given
        when(couponService.getByCode("nope")).thenThrow(new CouponNotFoundException("nope"));

        // when
        ResultActions result = getCoupon("nope");

        // then
        result.andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Coupon not found"))
                .andExpect(jsonPath("$.detail").value("No coupon found for code 'nope'"))
                .andExpect(jsonPath("$.instance").value("/api/v1/coupons/nope"));
    }

    private static Coupon sampleCoupon() {
        return new Coupon(CouponCode.of("summer"), Instant.parse("2026-01-01T00:00:00Z"),
                UsageLimit.of(3), 0, Country.of("PL"));
    }

    private ResultActions postCoupon(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getCoupon(String code) throws Exception {
        return mockMvc.perform(get("/api/v1/coupons/{code}", code));
    }

    /** Asserts the JSON body of {@link #sampleCoupon()}. */
    private static void expectSummerCouponBody(ResultActions result) throws Exception {
        result.andExpect(jsonPath("$.code").value("summer"))
                .andExpect(jsonPath("$.maxUses").value(3))
                .andExpect(jsonPath("$.currentUses").value(0))
                .andExpect(jsonPath("$.remainingUses").value(3))
                .andExpect(jsonPath("$.country").value("PL"));
    }
}
