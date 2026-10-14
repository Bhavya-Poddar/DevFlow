package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import com.devflow.devflow.config.WebSocketConfig;

@SpringBootTest
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
}
