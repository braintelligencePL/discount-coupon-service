package com.example.coupons.infrastructure.geoip;

import com.example.coupons.domain.exception.DomainValidationException;
import com.example.coupons.domain.model.Country;
import com.example.coupons.application.port.GeoIpResolver;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class IpApiGeoIpResolver implements GeoIpResolver {

    private static final Logger log = LoggerFactory.getLogger(IpApiGeoIpResolver.class);

    private final RestClient restClient;
    private final Cache<String, Country> cache;
    private final CircuitBreaker circuitBreaker;

    IpApiGeoIpResolver(RestClient geoIpRestClient,
                       Cache<String, Country> geoIpCache,
                       CircuitBreakerRegistry circuitBreakerRegistry) {
        this.restClient = geoIpRestClient;
        this.cache = geoIpCache;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("geoip");
    }

    @Override
    public Optional<Country> resolve(String ip) {
        if (isNonPublic(ip)) {
            return Optional.empty();
        }

        Country cached = cache.getIfPresent(ip);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<Country> result;
        try {
            result = CircuitBreaker.decorateSupplier(circuitBreaker, () -> callProvider(ip)).get();
        } catch (CallNotPermittedException e) {
            log.warn("geo-IP circuit open; treating {} as undetermined", ip);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("geo-IP lookup failed for {}: {}", ip, e.toString());
            return Optional.empty();
        }

        result.ifPresent(country -> cache.put(ip, country));
        return result;
    }

    private Optional<Country> callProvider(String ip) {
        IpApiResponse body = restClient.get()
                .uri("/json/{ip}?fields=status,countryCode", ip)
                .retrieve()
                .body(IpApiResponse.class);

        if (body == null || !"success".equals(body.status()) || body.countryCode() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Country.of(body.countryCode()));
        } catch (DomainValidationException e) {
            log.warn("geo-IP returned an unrecognised country code: {}", body.countryCode());
            return Optional.empty();
        }
    }

    private static boolean isNonPublic(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress()
                    || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return true;
        }
    }

    private record IpApiResponse(String status, String countryCode) {
    }
}
