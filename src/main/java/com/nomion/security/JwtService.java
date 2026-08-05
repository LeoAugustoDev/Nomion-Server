package com.nomion.security;

import com.nomion.config.JwtProperties;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {


    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String TENANT_SELECTION_TOKEN_TYPE = "TENANT_SELECTION";

    private final SecretKey signingKey;
    private final Duration accessTokenExpiration;
    private final Duration refreshTokenExpiration;
    private final Duration tenantSelectionTokenExpiration;


    public JwtService(JwtProperties properties) {

        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret deve possuir ao menos 256 bits");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiration = properties.accessTokenExpiration();
        this.refreshTokenExpiration = properties.refreshTokenExpiration();
        this.tenantSelectionTokenExpiration = properties.tenantSelectionTokenExpiration();
    }


    public String generateAccessToken(User user, TenantMembership membership) {

        Instant issuedAt = Instant.now();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("tokenType", ACCESS_TOKEN_TYPE)
                .claim("membershipId", membership.getId().toString())
                .claim("tenantId", membership.getTenant().getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessTokenExpiration)))
                .signWith(signingKey)
                .compact();
    }


    public String generateTenantSelectionToken(User user, UUID tokenId) {

        Instant issuedAt = Instant.now();

        return Jwts.builder()
                .id(tokenId.toString())
                .subject(user.getId().toString())
                .claim("tokenType", TENANT_SELECTION_TOKEN_TYPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(tenantSelectionTokenExpiration)))
                .signWith(signingKey)
                .compact();
    }


    public AuthenticatedUser extractAuthenticatedUser(String token) {

        Claims claims = parseClaims(token);

        validateTokenType(claims, ACCESS_TOKEN_TYPE);

        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get("membershipId", String.class)),
                UUID.fromString(claims.get("tenantId", String.class)),
                claims.get("email", String.class)
        );
    }


    public TenantSelectionPrincipal extractTenantSelectionPrincipal(String token) {

        Claims claims = parseClaims(token);

        validateTokenType(claims, TENANT_SELECTION_TOKEN_TYPE);

        return new TenantSelectionPrincipal(
                UUID.fromString(claims.getId()),
                UUID.fromString(claims.getSubject())
        );
    }


    public Duration getAccessTokenExpiration() {
        return accessTokenExpiration;
    }


    public Duration getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }


    public Duration getTenantSelectionTokenExpiration() {
        return tenantSelectionTokenExpiration;
    }


    private Claims parseClaims(String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


    private void validateTokenType(Claims claims, String expectedType) {

        if (!expectedType.equals(claims.get("tokenType", String.class))) {
            throw new JwtException("Tipo de token inválido");
        }
    }
}
