package com.nomion.auth.service;

import com.nomion.auth.domain.RefreshToken;
import com.nomion.auth.dto.LoginRequest;
import com.nomion.auth.dto.RefreshTokenRequest;
import com.nomion.auth.dto.TokenResponse;
import com.nomion.auth.repository.RefreshTokenRepository;
import com.nomion.handler.UnauthorizedException;
import com.nomion.security.JwtService;
import com.nomion.tenant.domain.Tenant;
import com.nomion.user.domain.User;
import com.nomion.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {


    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService service;


    @Test
    void shouldAuthenticateUserAndIssueTokens() {

        User user = createUser();

        when(userRepository.findByEmailIgnoreCase("leonardo@nomion.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha-segura", "encoded-password")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(15));
        when(jwtService.getRefreshTokenExpiration()).thenReturn(Duration.ofDays(7));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = service.login(new LoginRequest(
                "LEONARDO@NOMION.COM",
                "senha-segura"
        ));

        ArgumentCaptor<RefreshToken> refreshTokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);

        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());

        RefreshToken refreshToken = refreshTokenCaptor.getValue();

        assertEquals("access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900, response.expiresIn());
        assertNotNull(response.refreshToken());
        assertEquals(user, refreshToken.getUser());
        assertEquals(hashToken(response.refreshToken()), refreshToken.getTokenHash());
        assertTrue(refreshToken.getExpiresAt().isAfter(Instant.now()));
    }


    @Test
    void shouldRejectInvalidCredentials() {

        User user = createUser();

        when(userRepository.findByEmailIgnoreCase("leonardo@nomion.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha-invalida", "encoded-password")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> service.login(new LoginRequest(
                "leonardo@nomion.com",
                "senha-invalida"
        )));

        verify(refreshTokenRepository, never()).save(any());
    }


    @Test
    void shouldRotateRefreshToken() {

        User user = createUser();
        String refreshTokenValue = "refresh-token";

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshTokenValue))
                .expiresAt(Instant.now().plus(Duration.ofDays(1)))
                .build();

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(
                hashToken(refreshTokenValue)
        )).thenReturn(Optional.of(refreshToken));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(15));
        when(jwtService.getRefreshTokenExpiration()).thenReturn(Duration.ofDays(7));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = service.refresh(new RefreshTokenRequest(refreshTokenValue));

        assertEquals("new-access-token", response.accessToken());
        assertNotNull(response.refreshToken());
        assertNotNull(refreshToken.getRevokedAt());
    }


    @Test
    void shouldRejectExpiredRefreshToken() {

        String refreshTokenValue = "refresh-token-expirado";

        RefreshToken refreshToken = RefreshToken.builder()
                .user(createUser())
                .tokenHash(hashToken(refreshTokenValue))
                .expiresAt(Instant.now().minusSeconds(1))
                .build();

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(
                hashToken(refreshTokenValue)
        )).thenReturn(Optional.of(refreshToken));

        assertThrows(UnauthorizedException.class, () ->
                service.refresh(new RefreshTokenRequest(refreshTokenValue)));

        verify(refreshTokenRepository, never()).save(any());
    }


    private User createUser() {

        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .build();

        return User.builder()
                .id(UUID.randomUUID())
                .name("Leonardo Alves")
                .email("leonardo@nomion.com")
                .password("encoded-password")
                .tenant(tenant)
                .build();
    }


    private String hashToken(String token) {

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
