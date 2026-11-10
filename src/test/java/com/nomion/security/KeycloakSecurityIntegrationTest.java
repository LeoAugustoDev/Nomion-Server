package com.nomion.security;

import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.repository.TenantMembershipRepository;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.tenant.service.TenantMembershipService;
import com.nomion.user.domain.RoleCode;
import com.nomion.user.domain.User;
import com.nomion.user.repository.UserRepository;
import com.nomion.user.service.RoleAssignmentService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "keycloak"})
@Import(KeycloakSecurityIntegrationTest.PrincipalProbe.class)
@Transactional
class KeycloakSecurityIntegrationTest {

    private static final String ISSUER = "https://identity.example.test/realms/nomion";
    private static final String AUDIENCE = "nomion-api";
    private static final String PROBE = "/test/keycloak-principal";
    private static final String LOCAL_EMAIL = "keycloak-local@example.test";
    private static final RSAKey TRUSTED_KEY = createKey();
    private static HttpServer jwksServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private TenantMembershipService membershipService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @DynamicPropertySource
    static void resourceServerProperties(DynamicPropertyRegistry registry) throws IOException {
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] jwks = new JWKSet(TRUSTED_KEY.toPublicJWK()).toString()
                .getBytes(StandardCharsets.UTF_8);
        jwksServer.createContext("/jwks", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
            }
        });
        jwksServer.start();
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void shouldResolveExistingUserByIssuerAndSubjectWithoutGrantingTenantPermissions() throws Exception {
        String subject = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.builder()
                .name("Local user")
                .email(LOCAL_EMAIL)
                .password("legacy-password-hash")
                .build());
        UUID originalId = user.getId();
        user.setIdentityIssuer(ISSUER);
        user.setIdentitySubject(subject);
        users.saveAndFlush(user);

        String token = sign(TRUSTED_KEY, ISSUER, subject, AUDIENCE, Instant.now().plusSeconds(300));
        mockMvc.perform(get(PROBE).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId.toString()))
                .andExpect(jsonPath("$.email").value(LOCAL_EMAIL))
                .andExpect(jsonPath("$.tenantId").isEmpty())
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.permissions").isEmpty());

        mockMvc.perform(get("/contracts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/setup/initialize").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUnlinkedIdentityEvenWhenEmailMatches() throws Exception {
        users.saveAndFlush(User.builder()
                .name("Existing user")
                .email("different-keycloak-email@example.test")
                .password("legacy-password-hash")
                .build());

        reject(sign(TRUSTED_KEY, ISSUER, UUID.randomUUID().toString(), AUDIENCE,
                Instant.now().plusSeconds(300)));
    }

    @Test
    void shouldRejectWrongIssuerAudienceSignatureAndExpiredTokensForLinkedUser() throws Exception {
        String subject = UUID.randomUUID().toString();
        users.saveAndFlush(User.builder()
                .name("Local user")
                .email(LOCAL_EMAIL)
                .password("legacy-password-hash")
                .identityIssuer(ISSUER)
                .identitySubject(subject)
                .build());

        Instant future = Instant.now().plusSeconds(300);
        reject(sign(TRUSTED_KEY, ISSUER + "/other", subject, AUDIENCE, future));
        reject(sign(TRUSTED_KEY, ISSUER, subject, "other-api", future));
        reject(sign(createKey(), ISSUER, subject, AUDIENCE, future));
        reject(sign(TRUSTED_KEY, ISSUER, subject, AUDIENCE, Instant.now().minusSeconds(300)));
    }

    @Test
    void shouldSelectOnlyActiveMembershipsAndApplyTheirPermissionsOnEachRequest() throws Exception {
        String subject = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.builder()
                .name("Local user")
                .email(LOCAL_EMAIL)
                .password("legacy-password-hash")
                .identityIssuer(ISSUER)
                .identitySubject(subject)
                .build());
        Tenant adminTenant = tenants.saveAndFlush(Tenant.builder().name("Alfa").slug("keycloak-alfa").build());
        Tenant viewerTenant = tenants.saveAndFlush(Tenant.builder().name("Beta").slug("keycloak-beta").build());
        TenantMembership admin = membershipService.create(user, adminTenant);
        TenantMembership viewer = membershipService.create(user, viewerTenant);
        roleAssignmentService.assignTenantRoles(admin, Set.of(RoleCode.TENANT_ADMIN), user);
        roleAssignmentService.assignTenantRoles(viewer, Set.of(RoleCode.VIEWER), user);

        String bearer = "Bearer " + sign(
                TRUSTED_KEY, ISSUER, subject, AUDIENCE, Instant.now().plusSeconds(300)
        );
        String adminId = adminTenant.getId().toString();
        String viewerId = viewerTenant.getId().toString();

        mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.memberships[0].tenantId").value(adminId))
                .andExpect(jsonPath("$.user.memberships[1].tenantId").value(viewerId))
                .andExpect(jsonPath("$.activeTenantId").isEmpty())
                .andExpect(jsonPath("$.permissions").isEmpty());
        mockMvc.perform(get("/me").header("Authorization", bearer).header("X-Tenant-Id", adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTenantId").value(adminId))
                .andExpect(jsonPath("$.roles[0]").value("TENANT_ADMIN"));
        mockMvc.perform(get("/me").header("Authorization", bearer).header("X-Tenant-Id", viewerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTenantId").value(viewerId))
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"));
        mockMvc.perform(get("/contracts").header("Authorization", bearer).header("X-Tenant-Id", viewerId))
                .andExpect(status().isOk());
        String contract = """
                {
                  "publicEntityId": "11111111-1111-1111-1111-111111111111",
                  "number": "01/2026",
                  "object": "Serviço de teste",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31",
                  "status": "DRAFT"
                }
                """;
        mockMvc.perform(post("/contracts").header("Authorization", bearer).header("X-Tenant-Id", viewerId)
                        .contentType("application/json").content(contract))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/contracts").header("Authorization", bearer).header("X-Tenant-Id", adminId)
                        .contentType("application/json").content(contract))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tenants/" + viewerId).header("Authorization", bearer).header("X-Tenant-Id", adminId))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/users").header("Authorization", bearer).header("X-Tenant-Id", adminId))
                .andExpect(status().isForbidden());

        for (String invalid : new String[]{UUID.randomUUID().toString(), "1-1-1-1-1"}) {
            mockMvc.perform(get("/me").header("Authorization", bearer).header("X-Tenant-Id", invalid))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/me").header("Authorization", bearer).header("X-Tenant-Id", adminId, viewerId))
                .andExpect(status().isForbidden());

        viewer.setStatus(TenantMembershipStatus.SUSPENDED);
        memberships.saveAndFlush(viewer);
        mockMvc.perform(get("/contracts").header("Authorization", bearer).header("X-Tenant-Id", viewerId))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.memberships.length()").value(1));
    }

    private void reject(String token) throws Exception {
        mockMvc.perform(get(PROBE).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private static RSAKey createKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not create test signing key", exception);
        }
    }

    private String sign(RSAKey key, String issuer, String subject, String audience, Instant expiresAt)
            throws JOSEException {
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject(subject)
                        .audience(audience)
                        .expirationTime(Date.from(expiresAt))
                        .claim("typ", "Bearer")
                        .claim("email", "different-keycloak-email@example.test")
                        .claim("permissions", new String[]{"CONTRACT_READ"})
                        .build()
        );
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }

    @RestController
    @TestComponent
    static class PrincipalProbe {
        @GetMapping(PROBE)
        AuthenticatedUser principal(@AuthenticationPrincipal AuthenticatedUser user) {
            return user;
        }
    }
}
