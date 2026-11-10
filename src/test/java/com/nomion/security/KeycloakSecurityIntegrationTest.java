package com.nomion.security;

import com.nomion.user.domain.User;
import com.nomion.user.repository.UserRepository;
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
