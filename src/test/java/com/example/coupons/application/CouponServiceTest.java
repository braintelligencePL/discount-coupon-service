package com.example.coupons.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.coupons.application.dto.RedeemCoupon;
import com.example.coupons.application.port.CouponRedemptionRepository;
import com.example.coupons.application.port.CouponRepository;
import com.example.coupons.application.port.GeoIpResolver;
import com.example.coupons.domain.exception.CountryNotAllowedException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.Country;
import com.example.coupons.domain.model.UsageLimit;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the one redemption side-effect a black-box test cannot assert:
 * a country-blocked redemption must not touch the database. The redemption
 * outcomes themselves are covered end-to-end by {@code CouponApiIT},
 * {@code CouponRedemptionApiIT} and {@code CouponRedemptionCountryIT}.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-07T08:09:10Z"), ZoneOffset.UTC);

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponRedemptionRepository redemptionRepository;
    @Mock
    private GeoIpResolver geoIpResolver;

    private CouponService service;

    @BeforeEach
    void setUp() {
        service = new CouponService(couponRepository, redemptionRepository, geoIpResolver, CLOCK);
    }

    @Test
    @DisplayName("should run the country check before any database write")
    void should_run_the_country_check_before_any_database_write() {
        // given a PL-only coupon and a caller who resolves to DE
        Coupon plOnly = new Coupon(CouponCode.of("wiosna"), Instant.EPOCH, UsageLimit.of(1), 0, Country.of("PL"));
        when(couponRepository.findByCode(CouponCode.of("wiosna"))).thenReturn(Optional.of(plOnly));
        when(geoIpResolver.resolve(anyString())).thenReturn(Optional.of(Country.of("DE")));

        // when the redemption is attempted
        assertThatThrownBy(() -> service.redeem(new RedeemCoupon("WIOSNA", "user-1", "203.0.113.1")))
                .isInstanceOf(CountryNotAllowedException.class);

        // then nothing was written to the redemption store
        verifyNoInteractions(redemptionRepository);
    }
}
