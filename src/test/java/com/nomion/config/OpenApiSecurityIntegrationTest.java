package com.nomion.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSecurityIntegrationTest {


    @Autowired
    private MockMvc mockMvc;


    @Test
    void shouldDocumentBearerAuthenticationAndPublicOperations() throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.securitySchemes.bearerAuth.type"
                ).value("http"))
                .andExpect(jsonPath(
                        "$.components.securitySchemes.bearerAuth.scheme"
                ).value("bearer"))
                .andExpect(jsonPath(
                        "$.components.securitySchemes.bearerAuth.bearerFormat"
                ).value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath(
                        "$['paths']['/auth/login']['post']['security']"
                ).isEmpty())
                .andExpect(jsonPath(
                        "$['paths']['/setup/initialize']['post']['security']"
                ).isEmpty());
    }
}
