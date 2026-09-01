package com.example.coupons.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.coupons.domain.exception.AlreadyRedeemedException;
import com.example.coupons.domain.exception.CountryNotAllowedException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.Country;
import com.example.coupons.domain.model.UsageLimit;
import com.example.coupons.application.dto.RedeemCoupon;
import com.example.coupons.application.port.CouponRedemptionRepository;
import com.example.coupons.application.port.CouponRepository;
import com.example.coupons.application.port.GeoIpResolver;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for the two redemption ordering guarantees that a black-box integration
 * test cannot cleanly assert. Create / look-up and the individual failure outcomes
 * are covered end-to-end by {@code CouponApiIT}, {@code CouponRedemptionApiIT} and
 * {@code CouponRedemptionCountryIT}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    private Coupon coupon(int maxUses, int currentUses, String country) {
        return new Coupon(CouponCode.of("wiosna"), Instant.EPOCH, UsageLimit.of(maxUses), currentUses,
                Country.of(country));
    }

    private RedeemCoupon redeem() {
        return new RedeemCoupon("WIOSNA", "user-1", "203.0.113.1");
    }

    @Test
    @DisplayName("should insert the redemption row before updating the counter")
    void should_insert_the_redemption_row_before_updating_the_counter() {
        // given
        // coupon is at its cap AND the user already redeemed it: the caller must be told
        // ALREADY_REDEEMED, not USAGE_LIMIT_REACHED — so the insert must run first
        when(couponRepository.findByCode(CouponCode.of("wiosna"))).thenReturn(Optional.of(coupon(1, 1, "PL")));
        when(geoIpResolver.resolve(anyString())).thenReturn(Optional.of(Country.of("PL")));
        doThrow(new AlreadyRedeemedException(CouponCode.of("wiosna"), "user-1"))
                .when(redemptionRepository).insert(any());

        // when
        assertThatThrownBy(() -> service.redeem(redeem())).isInstanceOf(AlreadyRedeemedException.class);

        // then
        verify(couponRepository, never()).incrementUsageIfBelowLimit(any());
    }

    @Test
    @DisplayName("should run the country check before any database write")
    void should_run_the_country_check_before_any_database_write() {
        // given
        when(couponRepository.findByCode(CouponCode.of("wiosna"))).thenReturn(Optional.of(coupon(3, 0, "PL")));
        when(geoIpResolver.resolve(anyString())).thenReturn(Optional.of(Country.of("DE")));

        // when
        assertThatThrownBy(() -> service.redeem(redeem())).isInstanceOf(CountryNotAllowedException.class);

        // then
        verifyNoInteractions(redemptionRepository);
        verify(couponRepository, never()).incrementUsageIfBelowLimit(any());
    }
}
