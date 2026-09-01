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
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

        CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build());

        resolver = new IpApiGeoIpResolver(
                restClient,
                Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofMinutes(5)).build(),
                circuitBreakers);
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
    void resolves_country_on_a_successful_response() {
        stub(okJson("{\"status\":\"success\",\"countryCode\":\"PL\"}"));

        Optional<Country> resolved = resolver.resolve(PUBLIC_IP);

        assertThat(resolved).map(Country::value).contains("PL");
    }

    @Test
    void provider_fail_status_is_undetermined() {
        stub(okJson("{\"status\":\"fail\"}"));

        assertThat(resolver.resolve(PUBLIC_IP)).isEmpty();
    }

    @Test
    void a_successful_resolution_is_cached() {
        stub(okJson("{\"status\":\"success\",\"countryCode\":\"PL\"}"));

        resolver.resolve(PUBLIC_IP);
        resolver.resolve(PUBLIC_IP);

        assertThat(httpCalls()).isEqualTo(1);
    }

    @Test
    void a_non_public_ip_is_undetermined_without_calling_the_provider() {
        assertThat(resolver.resolve("127.0.0.1")).isEmpty();
        assertThat(resolver.resolve("10.0.0.1")).isEmpty();
        assertThat(httpCalls()).isZero();
    }

    @Test
    void repeated_provider_errors_stay_undetermined_and_open_the_circuit() {
        stub(aResponse().withStatus(500));

        int attempts = 8;
        long undetermined = IntStream.range(0, attempts)
                .mapToObj(i -> resolver.resolve("203.0.113." + i))
                .filter(Optional::isEmpty)
                .count();

        assertThat(undetermined).isEqualTo(attempts);
        // Once the breaker opens, later attempts short-circuit instead of calling out.
        assertThat(httpCalls()).isLessThan(attempts);
    }
}
