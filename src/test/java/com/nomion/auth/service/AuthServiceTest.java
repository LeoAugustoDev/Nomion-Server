package com.nomion.auth.service;

import com.nomion.auth.domain.RefreshToken;
import com.nomion.auth.domain.TenantSelectionToken;
import com.nomion.auth.dto.LoginRequest;
import com.nomion.auth.dto.LoginResponse;
import com.nomion.auth.dto.LoginStatus;
import com.nomion.auth.dto.RefreshTokenRequest;
import com.nomion.auth.dto.SelectTenantRequest;
import com.nomion.auth.dto.SwitchTenantRequest;
import com.nomion.auth.dto.TokenResponse;
import com.nomion.auth.repository.RefreshTokenRepository;
import com.nomion.auth.repository.TenantSelectionTokenRepository;
import com.nomion.handler.UnauthorizedException;
import com.nomion.security.AuthenticatedUser;
import com.nomion.security.AuthorizationContext;
import com.nomion.security.AuthorizationService;
import com.nomion.security.JwtService;
import com.nomion.security.TenantSelectionPrincipal;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.dto.TenantMembershipResponse;
import com.nomion.tenant.mapper.TenantMembershipMapper;
import com.nomion.tenant.service.TenantMembershipService;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private TenantSelectionTokenRepository selectionTokenRepository;

    @Mock
    private TenantMembershipService membershipService;

    @Mock
    private TenantMembershipMapper membershipMapper;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService service;


    @Test
    void shouldAuthenticateDirectlyWhenUserHasOneTenant() {

        User user = createUser();
        TenantMembership membership = createMembership(user, "tenant-a");
        TenantMembershipResponse membershipResponse = createMembershipResponse(membership);

        when(userRepository.findByEmailIgnoreCase("leonardo@nomion.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha-segura", "encoded-password")).thenReturn(true);
        when(membershipService.findActiveByUser(user.getId()))
                .thenReturn(List.of(membership));
        mockTokenIssuance(user, membership, membershipResponse, "access-token");

        LoginResponse response = service.login(new LoginRequest(
                "LEONARDO@NOMION.COM",
                "senha-segura"
        ));

        assertEquals(LoginStatus.AUTHENTICATED, response.status());
        assertNotNull(response.tokens());
        assertNull(response.tenantSelectionToken());
        assertEquals(List.of(membershipResponse), response.tenants());

        ArgumentCaptor<RefreshToken> refreshTokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);

        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());

        RefreshToken refreshToken = refreshTokenCaptor.getValue();

        assertEquals(membership, refreshToken.getMembership());
        assertEquals(
                hashToken(response.tokens().refreshToken()),
                refreshToken.getTokenHash()
        );
    }


    @Test
    void shouldRequestTenantSelectionWhenUserHasMultipleTenants() {

        User user = createUser();
        TenantMembership firstMembership = createMembership(user, "tenant-a");
        TenantMembership secondMembership = createMembership(user, "tenant-b");
        TenantMembershipResponse firstResponse = createMembershipResponse(firstMembership);
        TenantMembershipResponse secondResponse = createMembershipResponse(secondMembership);

        when(userRepository.findByEmailIgnoreCase("leonardo@nomion.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha-segura", "encoded-password")).thenReturn(true);
        when(membershipService.findActiveByUser(user.getId()))
                .thenReturn(List.of(firstMembership, secondMembership));
        when(selectionTokenRepository.save(any(TenantSelectionToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateTenantSelectionToken(eq(user), any(UUID.class)))
                .thenReturn("selection-token");
        when(jwtService.getTenantSelectionTokenExpiration()).thenReturn(Duration.ofMinutes(5));
        when(membershipMapper.toResponse(firstMembership)).thenReturn(firstResponse);
        when(membershipMapper.toResponse(secondMembership)).thenReturn(secondResponse);

        LoginResponse response = service.login(new LoginRequest(
                "leonardo@nomion.com",
                "senha-segura"
        ));

        assertEquals(LoginStatus.TENANT_SELECTION_REQUIRED, response.status());
        assertNull(response.tokens());
        assertEquals("selection-token", response.tenantSelectionToken());
        assertEquals(300, response.tenantSelectionExpiresIn());
        assertEquals(List.of(firstResponse, secondResponse), response.tenants());

        verify(refreshTokenRepository, never()).save(any());
    }


    @Test
    void shouldIssueTokensAfterTenantSelection() {

        User user = createUser();
        TenantMembership membership = createMembership(user, "tenant-a");
        TenantMembershipResponse membershipResponse = createMembershipResponse(membership);
        UUID selectionTokenId = UUID.randomUUID();
        TenantSelectionToken selectionToken = TenantSelectionToken.builder()
                .id(selectionTokenId)
                .user(user)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
                .build();

        when(jwtService.extractTenantSelectionPrincipal("selection-token"))
                .thenReturn(new TenantSelectionPrincipal(selectionTokenId, user.getId()));
        when(selectionTokenRepository.findByIdAndConsumedAtIsNull(selectionTokenId))
                .thenReturn(Optional.of(selectionToken));
        when(membershipService.findActive(user.getId(), membership.getTenant().getId()))
                .thenReturn(Optional.of(membership));
        mockTokenIssuance(user, membership, membershipResponse, "access-token");

        TokenResponse response = service.selectTenant(new SelectTenantRequest(
                "selection-token",
                membership.getTenant().getId()
        ));

        assertEquals("access-token", response.accessToken());
        assertEquals(membershipResponse, response.tenant());
        assertNotNull(selectionToken.getConsumedAt());
    }


    @Test
    void shouldRejectConsumedTenantSelectionToken() {

        UUID userId = UUID.randomUUID();
        UUID selectionTokenId = UUID.randomUUID();

        when(jwtService.extractTenantSelectionPrincipal("selection-token"))
                .thenReturn(new TenantSelectionPrincipal(selectionTokenId, userId));
        when(selectionTokenRepository.findByIdAndConsumedAtIsNull(selectionTokenId))
                .thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () ->
                service.selectTenant(new SelectTenantRequest(
                        "selection-token",
                        UUID.randomUUID()
                )));

        verify(refreshTokenRepository, never()).save(any());
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

        verify(membershipService, never()).findActiveByUser(any());
        verify(refreshTokenRepository, never()).save(any());
    }


    @Test
    void shouldRotateRefreshTokenWithinSameTenant() {

        User user = createUser();
        TenantMembership membership = createMembership(user, "tenant-a");
        TenantMembershipResponse membershipResponse = createMembershipResponse(membership);
        String refreshTokenValue = "refresh-token";

        RefreshToken refreshToken = createRefreshToken(membership, refreshTokenValue);

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(
                hashToken(refreshTokenValue)
        )).thenReturn(Optional.of(refreshToken));
        when(membershipService.findActive(user.getId(), membership.getTenant().getId()))
                .thenReturn(Optional.of(membership));
        mockTokenIssuance(user, membership, membershipResponse, "new-access-token");

        TokenResponse response = service.refresh(new RefreshTokenRequest(refreshTokenValue));

        assertEquals("new-access-token", response.accessToken());
        assertNotNull(response.refreshToken());
        assertNotNull(refreshToken.getRevokedAt());
    }


    @Test
    void shouldRevokeCurrentSessionAndSwitchTenant() {

        User user = createUser();
        TenantMembership currentMembership = createMembership(user, "tenant-a");
        TenantMembership targetMembership = createMembership(user, "tenant-b");
        TenantMembershipResponse targetResponse = createMembershipResponse(targetMembership);
        String refreshTokenValue = "refresh-token";

        RefreshToken refreshToken = createRefreshToken(
                currentMembership,
                refreshTokenValue
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                currentMembership.getId(),
                currentMembership.getTenant().getId(),
                user.getEmail(),
                Set.of("TENANT_ADMIN"),
                Set.of("TENANT_READ")
        );

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(
                hashToken(refreshTokenValue)
        )).thenReturn(Optional.of(refreshToken));
        when(membershipService.findActive(
                user.getId(),
                currentMembership.getTenant().getId()
        )).thenReturn(Optional.of(currentMembership));
        when(membershipService.findActive(user.getId(), targetMembership.getTenant().getId()))
                .thenReturn(Optional.of(targetMembership));
        mockTokenIssuance(user, targetMembership, targetResponse, "target-access-token");

        TokenResponse response = service.switchTenant(
                authenticatedUser,
                new SwitchTenantRequest(
                        targetMembership.getTenant().getId(),
                        refreshTokenValue
                )
        );

        assertEquals("target-access-token", response.accessToken());
        assertEquals(targetResponse, response.tenant());
        assertNotNull(refreshToken.getRevokedAt());
    }


    @Test
    void shouldRejectExpiredRefreshToken() {

        User user = createUser();
        TenantMembership membership = createMembership(user, "tenant-a");
        String refreshTokenValue = "refresh-token-expirado";

        RefreshToken refreshToken = RefreshToken.builder()
                .membership(membership)
                .tokenHash(hashToken(refreshTokenValue))
                .expiresAt(Instant.now().minusSeconds(1))
                .build();

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(
                hashToken(refreshTokenValue)
        )).thenReturn(Optional.of(refreshToken));

        assertThrows(UnauthorizedException.class, () ->
                service.refresh(new RefreshTokenRequest(refreshTokenValue)));

        verify(membershipService, never()).findActive(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }


    private void mockTokenIssuance(
            User user,
            TenantMembership membership,
            TenantMembershipResponse membershipResponse,
            String accessToken
    ) {

        AuthorizationContext authorizationContext = new AuthorizationContext(
                Set.of("TENANT_ADMIN"),
                Set.of("TENANT_READ", "USER_READ")
        );

        when(authorizationService.load(user.getId(), membership.getId()))
                .thenReturn(authorizationContext);
        when(jwtService.generateAccessToken(user, membership, authorizationContext))
                .thenReturn(accessToken);
        when(jwtService.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(15));
        when(jwtService.getRefreshTokenExpiration()).thenReturn(Duration.ofDays(7));
        when(membershipMapper.toResponse(membership)).thenReturn(membershipResponse);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }


    private User createUser() {

        return User.builder()
                .id(UUID.randomUUID())
                .name("Leonardo Alves")
                .email("leonardo@nomion.com")
                .password("encoded-password")
                .build();
    }


    private TenantMembership createMembership(User user, String slug) {

        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Escritório " + slug)
                .slug(slug)
                .build();

        return TenantMembership.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tenant(tenant)
                .status(TenantMembershipStatus.ACTIVE)
                .build();
    }


    private TenantMembershipResponse createMembershipResponse(TenantMembership membership) {

        return new TenantMembershipResponse(
                membership.getId(),
                membership.getTenant().getId(),
                membership.getTenant().getName(),
                membership.getTenant().getSlug(),
                membership.getStatus()
        );
    }


    private RefreshToken createRefreshToken(
            TenantMembership membership,
            String token
    ) {

        return RefreshToken.builder()
                .membership(membership)
                .tokenHash(hashToken(token))
                .expiresAt(Instant.now().plus(Duration.ofDays(1)))
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
