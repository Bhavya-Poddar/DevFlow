package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import com.devflow.devflow.config.WebSocketConfig;

@SpringBootTest(properties = "devflow.cors.allowed-origins=http://localhost:5173,https://frontend.example.test")
@AutoConfigureMockMvc
class WebSocketConfigurationTests {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private MockMvc mockMvc;

    @Test
    void websocketConfigurationStartsWithTheApplication() {
        assertTrue(applicationContext.containsBean("webSocketConfig"));
    }

    @Test
    void sockJsInfoIsPublicForTheConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(get("/ws/info").header("Origin", "http://localhost:5173"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void sockJsInfoAcceptsAnExplicitlyConfiguredProductionOrigin() throws Exception {
        mockMvc.perform(get("/ws/info").header("Origin", "https://frontend.example.test"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.test"));
    }

    @Test
    void sockJsInfoRejectsAnOriginOutsideTheAllowList() throws Exception {
        mockMvc.perform(get("/ws/info").header("Origin", "https://untrusted.example.test"))
            .andExpect(status().isForbidden());
    }

    @Test
    void restCorsAllowsConfiguredOriginAndRequiredAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/issues")
                .header("Origin", "https://frontend.example.test")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.test"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void restCorsRejectsOriginOutsideTheAllowList() throws Exception {
        mockMvc.perform(options("/api/issues")
                .header("Origin", "https://untrusted.example.test")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }
}
