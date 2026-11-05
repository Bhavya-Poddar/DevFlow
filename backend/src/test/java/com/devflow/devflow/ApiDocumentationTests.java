package com.devflow.devflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "devflow.api-docs.public-access=true"
})
@AutoConfigureMockMvc
class ApiDocumentationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiJsonDocumentsExistingRoutesAndJwtBearerSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("""
                    {
                      "info": {"title": "DevFlow API", "version": "v1"},
                      "paths": {"/api/issues": {"get": {"summary": "List tasks"}}},
                      "components": {"securitySchemes": {"bearerAuth": {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"}}}
                    }
                    """, false));
    }

    @Test
    void swaggerUiIsAvailableWhenDocumentationIsEnabled() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
