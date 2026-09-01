package com.example.coupons.infrastructure.geoip;

import com.example.coupons.domain.model.Country;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class GeoIpClientConfig {

    @Bean
    RestClient geoIpRestClient(GeoIpProperties properties, RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.timeout())
                .withReadTimeout(properties.timeout());
        return builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    @Bean
    Cache<String, Country> geoIpCache(GeoIpProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(properties.cache().maxSize())
                .expireAfterWrite(properties.cache().ttl())
                .build();
    }
}
