package com.nomion.security;

import com.nomion.config.JwtProperties;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.user.domain.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {


    private static final String SECRET = Base64.getEncoder().encodeToString(
            "secure-test-key-for-nomion-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)
    );


    @Test
    void shouldGenerateAccessTokenForActiveMembership() {

        JwtService service = createService();
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("leonardo@nomion.com")
                .build();

        TenantMembership membership = TenantMembership.builder()
                .id(membershipId)
                .user(user)
                .tenant(Tenant.builder().id(tenantId).build())
                .build();

        AuthorizationContext authorizationContext = new AuthorizationContext(
                Set.of("TENANT_ADMIN"),
                Set.of("TENANT_READ", "USER_READ")
        );

        String token = service.generateAccessToken(
                user,
                membership,
                authorizationContext
        );

        AuthenticatedUser authenticatedUser = service.extractAuthenticatedUser(token);

        assertEquals(userId, authenticatedUser.id());
        assertEquals(membershipId, authenticatedUser.membershipId());
        assertEquals(tenantId, authenticatedUser.tenantId());
        assertEquals("leonardo@nomion.com", authenticatedUser.email());
        assertEquals(Set.of("TENANT_ADMIN"), authenticatedUser.roles());
        assertEquals(
                Set.of("TENANT_READ", "USER_READ"),
                authenticatedUser.permissions()
        );
    }


    @Test
    void shouldGenerateTenantSelectionTokenWithoutAccessPermission() {

        JwtService service = createService();
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        User user = User.builder().id(userId).build();

        String token = service.generateTenantSelectionToken(user, tokenId);

        TenantSelectionPrincipal principal = service.extractTenantSelectionPrincipal(token);

        assertEquals(tokenId, principal.tokenId());
        assertEquals(userId, principal.userId());
        assertThrows(JwtException.class, () -> service.extractAuthenticatedUser(token));
    }


    @Test
    void shouldRejectInvalidToken() {

        JwtService service = createService();

        assertThrows(JwtException.class, () ->
                service.extractAuthenticatedUser("token-invalido"));
    }


    private JwtService createService() {

        return new JwtService(new JwtProperties(
                SECRET,
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                Duration.ofMinutes(5)
        ));
    }
}
