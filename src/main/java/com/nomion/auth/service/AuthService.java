package com.nomion.auth.service;

import com.nomion.auth.domain.RefreshToken;
import com.nomion.auth.dto.LoginRequest;
import com.nomion.auth.dto.RefreshTokenRequest;
import com.nomion.auth.dto.TokenResponse;
import com.nomion.auth.repository.RefreshTokenRepository;
import com.nomion.handler.UnauthorizedException;
import com.nomion.security.JwtService;
import com.nomion.user.domain.User;
import com.nomion.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {


    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;


    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }


    @Transactional
    public TokenResponse login(LoginRequest request) {

        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() ->
                        new UnauthorizedException("E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("E-mail ou senha inválidos");
        }

        return issueTokens(user);
    }


    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNull(hashToken(request.refreshToken()))
                .orElseThrow(() ->
                        new UnauthorizedException("Refresh token inválido"));

        if (!refreshToken.getExpiresAt().isAfter(Instant.now())) {
            throw new UnauthorizedException("Refresh token expirado");
        }

        refreshToken.setRevokedAt(Instant.now());

        return issueTokens(refreshToken.getUser());
    }


    @Transactional
    public void logout(RefreshTokenRequest request) {

        refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNull(hashToken(request.refreshToken()))
                .ifPresent(refreshToken -> refreshToken.setRevokedAt(Instant.now()));
    }


    private TokenResponse issueTokens(User user) {

        String refreshTokenValue = generateRefreshToken();

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshTokenValue))
                .expiresAt(Instant.now().plus(jwtService.getRefreshTokenExpiration()))
                .build());

        return new TokenResponse(
                jwtService.generateAccessToken(user),
                refreshTokenValue,
                "Bearer",
                jwtService.getAccessTokenExpiration().toSeconds()
        );
    }


    private String generateRefreshToken() {

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }


    private String hashToken(String token) {

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível", exception);
        }
    }


    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
