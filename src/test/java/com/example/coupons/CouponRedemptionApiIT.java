package com.example.coupons;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.coupons.support.StubGeoIpConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * End-to-end redemption flow over real HTTP against real PostgreSQL: the full
 * ladder of distinct outcomes for a {@code maxUses = 2} coupon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StubGeoIpConfiguration.class})
class CouponRedemptionApiIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("TRUNCATE TABLE coupon_redemption, coupon RESTART IDENTITY CASCADE");
        rest.postForEntity("/api/v1/coupons", json("{\"code\":\"rush\",\"maxUses\":2,\"country\":\"PL\"}"),
                JsonNode.class);
    }

    @Test
    @DisplayName("should produce a distinct outcome for each rung of the redemption ladder")
    void should_produce_a_distinct_outcome_for_each_rung_of_the_redemption_ladder() {
        // given a fresh maxUses=2 coupon "rush" (created in @BeforeEach)

        // when the first user redeems
        JsonNode first = redeem("rush", "u1").getBody();
        // then one use remains
        assertThat(first.get("remainingUses").asInt()).isEqualTo(1);

        // and when the second user redeems
        JsonNode second = redeem("rush", "u2").getBody();
        // then none remain
        assertThat(second.get("remainingUses").asInt()).isZero();

        // and when a third user redeems
        ResponseEntity<JsonNode> third = redeem("rush", "u3");
        // then the usage limit is reported
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(third.getBody().get("code").asText()).isEqualTo("USAGE_LIMIT_REACHED");

        // and when the first user redeems again
        ResponseEntity<JsonNode> repeat = redeem("rush", "u1");
        // then it is reported as already redeemed
        assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repeat.getBody().get("code").asText()).isEqualTo("ALREADY_REDEEMED");

        // and when an unknown coupon is redeemed
        ResponseEntity<JsonNode> missing = redeem("nope", "u9");
        // then it is not found
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().get("code").asText()).isEqualTo("COUPON_NOT_FOUND");
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
