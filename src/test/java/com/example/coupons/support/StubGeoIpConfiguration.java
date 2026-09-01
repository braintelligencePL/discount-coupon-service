package com.example.coupons.support;

import com.example.coupons.application.port.GeoIpResolver;
import com.example.coupons.domain.model.Country;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real {@link GeoIpResolver} with a deterministic stub for tests that
 * exercise redemption but don't care about geo-IP behavior itself — every coupon is
 * country-restricted, so redemption always resolves a caller country.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubGeoIpConfiguration {

    @Bean
    @Primary
    StubGeoIpResolver stubGeoIpResolver() {
        return new StubGeoIpResolver();
    }

    public static final class StubGeoIpResolver implements GeoIpResolver {
        /** Country resolved for every caller; defaults to PL, mutable per test. */
        public volatile Optional<Country> next = Optional.of(Country.of("PL"));

        @Override
        public Optional<Country> resolve(String ip) {
            return next;
        }
    }
}
