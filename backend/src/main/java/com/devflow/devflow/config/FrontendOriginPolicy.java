package com.devflow.devflow.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Shared allow-list used by REST CORS and the SockJS/STOMP handshake.
 */
@Component
public class FrontendOriginPolicy {

    private final List<String> allowedOrigins;

    public FrontendOriginPolicy(@Value("${devflow.cors.allowed-origins:http://localhost:5173}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .peek(FrontendOriginPolicy::validateOrigin)
                .distinct()
                .toList();

        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("devflow.cors.allowed-origins must contain at least one frontend origin");
        }
    }

    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    private static void validateOrigin(String origin) {
        try {
            URI uri = new URI(origin);
            String scheme = uri.getScheme();
            if ("*".equals(origin)
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
                throw new IllegalArgumentException("devflow.cors.allowed-origins must contain exact HTTP(S) origins without paths or wildcards");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("devflow.cors.allowed-origins contains an invalid origin", exception);
        }
    }
}
