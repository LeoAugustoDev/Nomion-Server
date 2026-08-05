package com.nomion.security;

import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;


    @Test
    void shouldRequireAuthenticationToSwitchTenant() throws Exception {

        mockMvc.perform(post("/auth/switch-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "11111111-1111-1111-1111-111111111111",
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }


    @Test
    void shouldKeepLoginPublicAndValidateRequest() throws Exception {

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }


    @Test
    void shouldRejectTenantSelectionTokenAsAccessToken() throws Exception {

        String selectionToken = jwtService.generateTenantSelectionToken(
                User.builder().id(UUID.randomUUID()).build(),
                UUID.randomUUID()
        );

        mockMvc.perform(get("/users/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + selectionToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }


    @Test
    void shouldDenyAdministrativeCreationBeforeRbac() throws Exception {

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("admin@nomion.com")
                .build();

        TenantMembership membership = TenantMembership.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tenant(Tenant.builder().id(UUID.randomUUID()).build())
                .build();

        String accessToken = jwtService.generateAccessToken(user, membership);

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Novo usuário",
                                  "email": "novo@nomion.com",
                                  "password": "senha-segura",
                                  "tenantId": "11111111-1111-1111-1111-111111111111"
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
