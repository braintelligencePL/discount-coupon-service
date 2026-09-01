package com.example.coupons.infrastructure.geoip;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "geoip")
public record GeoIpProperties(
        @DefaultValue("http://ip-api.com") URI baseUrl,
        @DefaultValue("2s") Duration timeout,
        @DefaultValue Cache cache,
        @DefaultValue("false") boolean allowIpOverride,
        @DefaultValue List<String> trustedProxies) {

    public record Cache(
            @DefaultValue("10m") Duration ttl,
            @DefaultValue("10000") long maxSize) {
    }
}
