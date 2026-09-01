package com.example.coupons.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Determines the caller's IP for the country check.
 *
 * <p>Falls back to {@link HttpServletRequest#getRemoteAddr()}, the direct TCP peer
 * address. <b>Known gap:</b> behind a load balancer / reverse proxy this returns the
 * proxy's IP, not the real caller's, breaking the country check for every request.
 * Fixing it needs {@code server.forward-headers-strategy=framework} (registers
 * Spring's {@code ForwardedHeaderFilter} to rewrite the remote address from
 * {@code X-Forwarded-For}) plus infra that strips any client-supplied
 * {@code X-Forwarded-For} before it reaches the app. Not set up here — revisit
 * before deploying behind a proxy.
 *
 * <p>Only when {@code geoip.allow-ip-override=true} (a dev/test flag, off in
 * production) may a request name its own IP via the {@code X-Client-IP} header or
 * an {@code ip} query parameter, so the country rule can be exercised locally
 * without standing up a load balancer.
 */
@Component
public class ClientIpResolver {

    static final String OVERRIDE_HEADER = "X-Client-IP";
    static final String OVERRIDE_PARAM = "ip";

    private final boolean allowOverride;

    ClientIpResolver(@Value("${geoip.allow-ip-override:false}") boolean allowOverride) {
        this.allowOverride = allowOverride;
    }

    public String resolve(HttpServletRequest request) {
        if (allowOverride) {
            String header = request.getHeader(OVERRIDE_HEADER);
            if (header != null && !header.isBlank()) {
                return header.strip();
            }
            String param = request.getParameter(OVERRIDE_PARAM);
            if (param != null && !param.isBlank()) {
                return param.strip();
            }
        }
        return request.getRemoteAddr();
    }
}
