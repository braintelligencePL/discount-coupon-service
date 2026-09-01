package com.example.coupons;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.coupons.domain.model.Country;
import com.example.coupons.support.StubGeoIpConfiguration;
import com.example.coupons.support.StubGeoIpConfiguration.StubGeoIpResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StubGeoIpConfiguration.class})
class CouponRedemptionCountryIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StubGeoIpResolver geoIp;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("TRUNCATE TABLE coupon_redemption, coupon RESTART IDENTITY CASCADE");
    }

    @Test
    void a_country_restricted_coupon_enforces_the_resolved_country() {
        createCoupon("pl-only", "PL");

        geoIp.next = Optional.of(Country.of("PL"));
        assertThat(redeem("pl-only", "u1").getStatusCode()).isEqualTo(HttpStatus.OK);

        geoIp.next = Optional.of(Country.of("DE"));
        ResponseEntity<JsonNode> blocked = redeem("pl-only", "u2");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.getBody().get("code").asText()).isEqualTo("COUNTRY_NOT_ALLOWED");

        geoIp.next = Optional.empty();
        ResponseEntity<JsonNode> undetermined = redeem("pl-only", "u3");
        assertThat(undetermined.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(undetermined.getBody().get("code").asText()).isEqualTo("COUNTRY_NOT_DETERMINED");
    }

    @Test
    void creating_a_coupon_without_a_country_is_rejected() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/v1/coupons",
                json("{\"code\":\"no-country\",\"maxUses\":5}"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    private void createCoupon(String code, String country) {
        rest.postForEntity("/api/v1/coupons",
                json("{\"code\":\"" + code + "\",\"maxUses\":5,\"country\":\"" + country + "\"}"), JsonNode.class);
    }

    private ResponseEntity<JsonNode> redeem(String code, String userId) {
        return rest.postForEntity("/api/v1/coupons/{code}/redemptions",
                json("{\"userId\":\"" + userId + "\"}"), JsonNode.class, code);
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
