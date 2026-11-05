package com.devflow.devflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.env.Environment;

@SpringBootTest
@AutoConfigureMockMvc
class HealthProbeSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void livenessProbeIsPublicAndDoesNotExposeComponents() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @Test
    void readinessProbeIsPublicAndDoesNotExposeComponents() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @Test
    void otherActuatorEndpointsAreNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJsonUsesTheStandardErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"status\":400,\"errorCode\":\"INVALID_REQUEST\",\"path\":\"/api/auth/login\"}"));
    }

    @Test
    void protectedRestRouteUsesStandardUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get("/api/issues"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"status\":401,\"errorCode\":\"UNAUTHORIZED\",\"path\":\"/api/issues\"}"));
    }

    @Test
    void openApiIsDisabledOutsideTheDevelopmentProfileByDefault() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals("false", environment.getProperty("springdoc.api-docs.enabled"));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }
}
