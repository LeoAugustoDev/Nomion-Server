package com.nomion.security;

import com.nomion.config.JwtProperties;
import com.nomion.user.domain.User;
import io.jsonwebtoken.Claims;
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


    private final SecretKey signingKey;
    private final Duration accessTokenExpiration;
    private final Duration refreshTokenExpiration;


    public JwtService(JwtProperties properties) {

        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret deve possuir ao menos 256 bits");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiration = properties.accessTokenExpiration();
        this.refreshTokenExpiration = properties.refreshTokenExpiration();
    }


    public String generateAccessToken(User user) {

        Instant issuedAt = Instant.now();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("tenantId", user.getTenant().getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessTokenExpiration)))
                .signWith(signingKey)
                .compact();
    }


    public AuthenticatedUser extractAuthenticatedUser(String token) {

        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get("tenantId", String.class)),
                claims.get("email", String.class)
        );
    }


    public Duration getAccessTokenExpiration() {
        return accessTokenExpiration;
    }


    public Duration getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
