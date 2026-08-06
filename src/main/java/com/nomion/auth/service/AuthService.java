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
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.mapper.TenantMembershipMapper;
import com.nomion.tenant.service.TenantMembershipService;
import com.nomion.user.domain.User;
import com.nomion.user.dto.UserInfoResponse;
import com.nomion.user.mapper.UserMapper;
import com.nomion.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {


    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantSelectionTokenRepository selectionTokenRepository;
    private final TenantMembershipService membershipService;
    private final TenantMembershipMapper membershipMapper;
    private final AuthorizationService authorizationService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;


    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            TenantSelectionTokenRepository selectionTokenRepository,
            TenantMembershipService membershipService,
            TenantMembershipMapper membershipMapper,
            AuthorizationService authorizationService,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.selectionTokenRepository = selectionTokenRepository;
        this.membershipService = membershipService;
        this.membershipMapper = membershipMapper;
        this.authorizationService = authorizationService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }


    @Transactional
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() ->
                        new UnauthorizedException("E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("E-mail ou senha inválidos");
        }

        List<TenantMembership> memberships = membershipService.findActiveByUser(user.getId());

        if (memberships.isEmpty()) {
            throw new UnauthorizedException("Usuário não possui acesso ativo a nenhum tenant");
        }

        UserInfoResponse userInfo = userMapper.toInfo(user);

        if (memberships.size() == 1) {
            TenantMembership membership = memberships.getFirst();

            return new LoginResponse(
                    LoginStatus.AUTHENTICATED,
                    userInfo,
                    issueTokens(user, membership),
                    null,
                    0,
                    List.of(membershipMapper.toResponse(membership))
            );
        }

        UUID selectionTokenId = UUID.randomUUID();

        selectionTokenRepository.save(TenantSelectionToken.builder()
                .id(selectionTokenId)
                .user(user)
                .expiresAt(Instant.now().plus(
                        jwtService.getTenantSelectionTokenExpiration()
                ))
                .build());

        return new LoginResponse(
                LoginStatus.TENANT_SELECTION_REQUIRED,
                userInfo,
                null,
                jwtService.generateTenantSelectionToken(user, selectionTokenId),
                jwtService.getTenantSelectionTokenExpiration().toSeconds(),
                memberships.stream()
                        .map(membershipMapper::toResponse)
                        .toList()
        );
    }


    @Transactional
    public TokenResponse selectTenant(SelectTenantRequest request) {

        TenantSelectionPrincipal principal = extractTenantSelectionPrincipal(
                request.tenantSelectionToken()
        );

        TenantSelectionToken selectionToken = selectionTokenRepository
                .findByIdAndConsumedAtIsNull(principal.tokenId())
                .orElseThrow(() ->
                        new UnauthorizedException("Token de seleção inválido"));

        if (!selectionToken.getUser().getId().equals(principal.userId())
                || !selectionToken.getExpiresAt().isAfter(Instant.now())) {
            throw new UnauthorizedException("Token de seleção inválido ou expirado");
        }

        TenantMembership membership = findActiveMembership(
                principal.userId(),
                request.tenantId()
        );

        selectionToken.setConsumedAt(Instant.now());

        return issueTokens(selectionToken.getUser(), membership);
    }


    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {

        RefreshToken refreshToken = findValidRefreshToken(request.refreshToken());
        TenantMembership membership = validateActiveMembership(refreshToken);

        refreshToken.setRevokedAt(Instant.now());

        return issueTokens(membership.getUser(), membership);
    }


    @Transactional
    public TokenResponse switchTenant(
            AuthenticatedUser authenticatedUser,
            SwitchTenantRequest request
    ) {

        if (authenticatedUser == null) {
            throw new UnauthorizedException("Autenticação necessária");
        }

        RefreshToken refreshToken = findValidRefreshToken(request.refreshToken());
        TenantMembership currentMembership = validateActiveMembership(refreshToken);

        if (!currentMembership.getUser().getId().equals(authenticatedUser.id())
                || !currentMembership.getId().equals(authenticatedUser.membershipId())
                || !currentMembership.getTenant().getId().equals(authenticatedUser.tenantId())) {
            throw new UnauthorizedException("Refresh token não pertence à sessão atual");
        }

        TenantMembership targetMembership = findActiveMembership(
                authenticatedUser.id(),
                request.tenantId()
        );

        refreshToken.setRevokedAt(Instant.now());

        return issueTokens(currentMembership.getUser(), targetMembership);
    }


    @Transactional
    public void logout(RefreshTokenRequest request) {

        refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNull(hashToken(request.refreshToken()))
                .ifPresent(refreshToken -> refreshToken.setRevokedAt(Instant.now()));
    }


    private TokenResponse issueTokens(
            User user,
            TenantMembership membership
    ) {

        String refreshTokenValue = generateRefreshToken();
        AuthorizationContext authorizationContext = authorizationService.load(
                user.getId(),
                membership.getId()
        );

        refreshTokenRepository.save(RefreshToken.builder()
                .membership(membership)
                .tokenHash(hashToken(refreshTokenValue))
                .expiresAt(Instant.now().plus(jwtService.getRefreshTokenExpiration()))
                .build());

        return new TokenResponse(
                jwtService.generateAccessToken(user, membership, authorizationContext),
                refreshTokenValue,
                "Bearer",
                jwtService.getAccessTokenExpiration().toSeconds(),
                membershipMapper.toResponse(membership),
                authorizationContext.roles(),
                authorizationContext.permissions()
        );
    }


    private RefreshToken findValidRefreshToken(String token) {

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNull(hashToken(token))
                .orElseThrow(() ->
                        new UnauthorizedException("Refresh token inválido"));

        if (!refreshToken.getExpiresAt().isAfter(Instant.now())) {
            throw new UnauthorizedException("Refresh token expirado");
        }

        return refreshToken;
    }


    private TenantMembership findActiveMembership(UUID userId, UUID tenantId) {

        return membershipService.findActive(userId, tenantId)
                .orElseThrow(() ->
                        new UnauthorizedException("Usuário não possui acesso ativo ao tenant"));
    }


    private TenantMembership validateActiveMembership(RefreshToken refreshToken) {

        TenantMembership tokenMembership = refreshToken.getMembership();

        TenantMembership activeMembership = findActiveMembership(
                tokenMembership.getUser().getId(),
                tokenMembership.getTenant().getId()
        );

        if (!activeMembership.getId().equals(tokenMembership.getId())) {
            throw new UnauthorizedException("Refresh token não pertence ao vínculo ativo");
        }

        return activeMembership;
    }


    private TenantSelectionPrincipal extractTenantSelectionPrincipal(String token) {

        try {
            return jwtService.extractTenantSelectionPrincipal(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException("Token de seleção inválido");
        }
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
