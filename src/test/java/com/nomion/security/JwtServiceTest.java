package com.nomion.security;

import com.nomion.config.JwtProperties;
import com.nomion.tenant.domain.Tenant;
import com.nomion.user.domain.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {


    private static final String SECRET = Base64.getEncoder().encodeToString(
            "secure-test-key-for-nomion-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)
    );


    @Test
    void shouldGenerateAndExtractAuthenticatedUser() {

        JwtService service = new JwtService(new JwtProperties(
                SECRET,
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        ));

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("leonardo@nomion.com")
                .tenant(Tenant.builder().id(tenantId).build())
                .build();

        String token = service.generateAccessToken(user);

        AuthenticatedUser authenticatedUser = service.extractAuthenticatedUser(token);

        assertEquals(userId, authenticatedUser.id());
        assertEquals(tenantId, authenticatedUser.tenantId());
        assertEquals("leonardo@nomion.com", authenticatedUser.email());
    }


    @Test
    void shouldRejectInvalidToken() {

        JwtService service = new JwtService(new JwtProperties(
                SECRET,
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        ));

        assertThrows(JwtException.class, () ->
                service.extractAuthenticatedUser("token-invalido"));
    }
}
