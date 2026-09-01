package com.example.coupons;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * End-to-end coupon create / look-up over real HTTP against real PostgreSQL —
 * proves the controller + service + persistence + problem+json wiring, including the
 * real case-insensitive unique index. Per-case validation and error mapping are
 * covered by {@code CouponControllerTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CouponApiIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("TRUNCATE TABLE coupon_redemption, coupon RESTART IDENTITY CASCADE");
    }

    @Test
    void creates_fetches_case_insensitively_and_404s_on_a_miss() {
        ResponseEntity<JsonNode> created = rest.postForEntity("/api/v1/coupons",
                json("{\"code\":\"AUTUMN\",\"maxUses\":2,\"country\":\"PL\"}"), JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getBody().get("code").asText()).isEqualTo("autumn");
        assertThat(created.getBody().get("remainingUses").asInt()).isEqualTo(2);

        ResponseEntity<JsonNode> fetched = rest.getForEntity("/api/v1/coupons/{code}", JsonNode.class, "autumn");
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("code").asText()).isEqualTo("autumn");

        ResponseEntity<JsonNode> miss = rest.getForEntity("/api/v1/coupons/{code}", JsonNode.class, "nope");
        assertThat(miss.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(miss.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(miss.getBody().get("code").asText()).isEqualTo("COUPON_NOT_FOUND");

        // a code that differs only in case is a duplicate at the database level
        ResponseEntity<JsonNode> dup = rest.postForEntity("/api/v1/coupons",
                json("{\"code\":\"Autumn\",\"maxUses\":9,\"country\":\"PL\"}"), JsonNode.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody().get("code").asText()).isEqualTo("DUPLICATE_CODE");
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
