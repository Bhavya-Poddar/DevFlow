package com.devflow.devflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "devflow.rate-limit.max-tracked-keys=100",
    "devflow.rate-limit.login.max-requests=2",
    "devflow.rate-limit.login.window=10m",
    "devflow.github.enabled=false"
})
class SensitiveEndpointRateLimitIntegrationTests {
    @Autowired private MockMvc mockMvc;

    @Test
    void loginAttemptsShareAnIpBucketEvenWhenTheSubmittedEmailChanges() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(request -> { request.setRemoteAddr("192.0.2.41"); return request; })
                .contentType("application/json")
                .content("{\"email\":\"first@example.test\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                .with(request -> { request.setRemoteAddr("192.0.2.41"); return request; })
                .contentType("application/json")
                .content("{\"email\":\"different@example.test\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr("192.0.2.41");
                    request.addHeader("X-Forwarded-For", "198.51.100.77");
                    return request;
                })
                .contentType("application/json")
                .content("{\"email\":\"third@example.test\",\"password\":\"wrong\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."))
            .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void retiredWebhookPathIsNotPublicOrRateLimitedAsAnActiveIntegration() throws Exception {
        mockMvc.perform(post("/api/integrations/github/webhook")
                .with(request -> { request.setRemoteAddr("192.0.2.42"); return request; }))
            .andExpect(status().isUnauthorized());
    }
}
