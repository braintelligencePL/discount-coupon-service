package com.example.coupons.application.port;

import com.example.coupons.domain.model.Country;
import java.util.Optional;

public interface GeoIpResolver {

    Optional<Country> resolve(String ip);
}
