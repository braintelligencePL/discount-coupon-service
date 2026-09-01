package com.example.coupons.infrastructure.geoip;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.coupons.domain.model.Country;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

class IpApiGeoIpResolverTest {

    private static final String PUBLIC_IP = "203.0.113.10";

    private WireMockServer wireMock;
    private IpApiGeoIpResolver resolver;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        ClientHttpRequestFactorySettings timeouts = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(250))
                .withReadTimeout(Duration.ofMillis(250));
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .requestFactory(ClientHttpRequestFactories.get(timeouts))
                .build();

        resolver = new IpApiGeoIpResolver(
                restClient,
                Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofMinutes(5)).build());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private void stub(com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathMatching("/json/.*")).willReturn(response));
    }

    private int httpCalls() {
        return wireMock.findAll(getRequestedFor(urlPathMatching("/json/.*"))).size();
    }

    @Test
    @DisplayName("should resolve the country on a successful response")
    void should_resolve_the_country_on_a_successful_response() {
        // given
        stub(okJson("{\"status\":\"success\",\"countryCode\":\"PL\"}"));

        // when
        Optional<Country> resolved = resolver.resolve(PUBLIC_IP);

        // then
        assertThat(resolved).map(Country::value).contains("PL");
    }

    @Test
    @DisplayName("should be undetermined when the provider reports failure")
    void should_be_undetermined_when_the_provider_reports_failure() {
        // given
        stub(okJson("{\"status\":\"fail\"}"));

        // then
        assertThat(resolver.resolve(PUBLIC_IP)).isEmpty();
    }

    @Test
    @DisplayName("should be undetermined on an HTTP error from the provider")
    void should_be_undetermined_on_an_http_error_from_the_provider() {
        // given
        stub(aResponse().withStatus(500));

        // then
        assertThat(resolver.resolve(PUBLIC_IP)).isEmpty();
    }

    @Test
    @DisplayName("should cache a successful resolution")
    void should_cache_a_successful_resolution() {
        // given
        stub(okJson("{\"status\":\"success\",\"countryCode\":\"PL\"}"));

        // when
        resolver.resolve(PUBLIC_IP);
        resolver.resolve(PUBLIC_IP);

        // then
        assertThat(httpCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("should not cache an undetermined result")
    void should_not_cache_an_undetermined_result() {
        // given
        stub(aResponse().withStatus(500));

        // when
        resolver.resolve(PUBLIC_IP);
        resolver.resolve(PUBLIC_IP);

        // then each attempt hits the provider again
        assertThat(httpCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("should not call the provider for a loopback IP")
    void should_not_call_the_provider_for_a_loopback_ip() {
        // then
        assertThat(resolver.resolve("127.0.0.1")).isEmpty();
        assertThat(httpCalls()).isZero();
    }

    @Test
    @DisplayName("should not call the provider for a private network IP")
    void should_not_call_the_provider_for_a_private_network_ip() {
        // then
        assertThat(resolver.resolve("10.0.0.1")).isEmpty();
        assertThat(httpCalls()).isZero();
    }
}
