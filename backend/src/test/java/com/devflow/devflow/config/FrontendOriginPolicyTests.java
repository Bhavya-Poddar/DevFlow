package com.devflow.devflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class FrontendOriginPolicyTests {

    @Test
    void acceptsCommaSeparatedExactHttpOriginsAndTrimsWhitespace() {
        FrontendOriginPolicy policy = new FrontendOriginPolicy(
                "http://localhost:5173, https://frontend.example.test");

        assertEquals(List.of("http://localhost:5173", "https://frontend.example.test"), policy.allowedOrigins());
    }

    @Test
    void rejectsWildcardOriginWhenCredentialsAreEnabled() {
        assertThrows(IllegalArgumentException.class, () -> new FrontendOriginPolicy("*"));
    }

    @Test
    void rejectsOriginsThatContainAPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new FrontendOriginPolicy("https://frontend.example.test/app"));
    }
}
