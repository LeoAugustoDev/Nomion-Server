package com.nomion.security;

import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Set;

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

    @Autowired
    private TenantRepository tenantRepository;


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
    void shouldDenyAdministrativeCreationWithoutPermission() throws Exception {

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("admin@nomion.com")
                .build();

        TenantMembership membership = TenantMembership.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tenant(Tenant.builder().id(UUID.randomUUID()).build())
                .build();

        String accessToken = jwtService.generateAccessToken(
                user,
                membership,
                new AuthorizationContext(Set.of("VIEWER"), Set.of("TENANT_READ"))
        );

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Novo usuário",
                                  "email": "novo@nomion.com",
                                  "password": "senha-segura",
                                  "roles": ["VIEWER"]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acesso negado"));
    }


    @Test
    @Transactional
    void shouldAllowTenantReadWithPermission() throws Exception {

        Tenant tenant = tenantRepository.saveAndFlush(Tenant.builder()
                .name("Tenant autorizado")
                .slug("tenant-autorizado-" + UUID.randomUUID())
                .build());
        String accessToken = accessToken(
                tenant.getId(),
                Set.of("VIEWER"),
                Set.of("TENANT_READ")
        );

        mockMvc.perform(get("/tenants/{id}", tenant.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenant.getId().toString()))
                .andExpect(jsonPath("$.name").value("Tenant autorizado"));
    }


    @Test
    @Transactional
    void shouldHideAnotherTenantEvenWithReadPermission() throws Exception {

        Tenant requestedTenant = tenantRepository.saveAndFlush(Tenant.builder()
                .name("Tenant protegido")
                .slug("tenant-protegido-" + UUID.randomUUID())
                .build());
        String accessToken = accessToken(
                UUID.randomUUID(),
                Set.of("TENANT_ADMIN"),
                Set.of("TENANT_READ")
        );

        mockMvc.perform(get("/tenants/{id}", requestedTenant.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }


    @Test
    @Transactional
    void shouldAllowPlatformAdministratorToReadAnotherTenant() throws Exception {

        Tenant requestedTenant = tenantRepository.saveAndFlush(Tenant.builder()
                .name("Tenant da plataforma")
                .slug("tenant-plataforma-" + UUID.randomUUID())
                .build());
        String accessToken = accessToken(
                UUID.randomUUID(),
                Set.of("PLATFORM_ADMIN"),
                Set.of("TENANT_READ")
        );

        mockMvc.perform(get("/tenants/{id}", requestedTenant.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestedTenant.getId().toString()));
    }


    private String accessToken(
            UUID tenantId,
            Set<String> roles,
            Set<String> permissions
    ) {

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("admin@nomion.com")
                .build();
        TenantMembership membership = TenantMembership.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tenant(Tenant.builder().id(tenantId).build())
                .build();

        return jwtService.generateAccessToken(
                user,
                membership,
                new AuthorizationContext(roles, permissions)
        );
    }
}
