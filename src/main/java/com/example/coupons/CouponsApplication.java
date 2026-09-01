package com.example.coupons;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/**
 * Entry point for the coupon discount service.
 *
 * <p>The application is organised as a pragmatic layered architecture, with
 * dependencies pointing inward ({@code api} &#8594; {@code application} &#8594;
 * {@code domain}):
 * <ul>
 *   <li>{@code api} — controllers and request/response DTOs</li>
 *   <li>{@code application} — use-case orchestration, the port interfaces and the
 *       transaction boundary</li>
 *   <li>{@code domain} — framework-free business model and rules</li>
 *   <li>{@code infrastructure} — {@code web} (inbound HTTP glue: problem+json
 *       mapping, correlation-id filter, client-IP resolution) plus {@code persistence}
 *       and the {@code geoip} client, the latter two implementing the ports the
 *       application layer owns</li>
 * </ul>
 *
 * <p>This class is the composition root: it sits outside the four layers and
 * wires the Spring context together.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CouponsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponsApplication.class, args);
    }

    /** A UTC clock, injected wherever "now" is needed so time can be controlled in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
