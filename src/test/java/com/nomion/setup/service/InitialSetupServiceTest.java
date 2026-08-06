package com.nomion.setup.service;

import com.nomion.config.InitialSetupProperties;
import com.nomion.handler.BusinessException;
import com.nomion.handler.ResourceNotFoundException;
import com.nomion.handler.UnauthorizedException;
import com.nomion.setup.domain.InitialSetupState;
import com.nomion.setup.dto.InitialAdminRequest;
import com.nomion.setup.dto.InitialSetupRequest;
import com.nomion.setup.dto.InitialSetupResponse;
import com.nomion.setup.repository.InitialSetupStateRepository;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.dto.CreateTenantRequest;
import com.nomion.tenant.dto.TenantMembershipResponse;
import com.nomion.tenant.dto.TenantResponse;
import com.nomion.tenant.mapper.TenantMapper;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.tenant.service.TenantMembershipService;
import com.nomion.user.domain.RoleCode;
import com.nomion.user.domain.User;
import com.nomion.user.dto.UserResponse;
import com.nomion.user.mapper.UserMapper;
import com.nomion.user.repository.UserRepository;
import com.nomion.user.service.RoleAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialSetupServiceTest {


    private static final String SETUP_TOKEN =
            "0123456789abcdef0123456789abcdef";


    @Mock
    private InitialSetupProperties properties;

    @Mock
    private InitialSetupStateRepository stateRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantMembershipService membershipService;

    @Mock
    private RoleAssignmentService roleAssignmentService;

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private InitialSetupState state;

    @InjectMocks
    private InitialSetupService service;


    @Test
    void shouldInitializeTenantAndAdministratorWithRequiredRoles() {

        InitialSetupRequest request = request();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        Instant initializedAt = Instant.now();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Nomion")
                .slug("nomion")
                .build();
        User administrator = User.builder()
                .id(userId)
                .name("Administrador Nomion")
                .email("admin@nomion.com")
                .password("encoded-password")
                .build();
        TenantMembership membership = TenantMembership.builder()
                .id(membershipId)
                .tenant(tenant)
                .user(administrator)
                .status(TenantMembershipStatus.ACTIVE)
                .build();
        TenantResponse tenantResponse = new TenantResponse(
                tenantId,
                "Nomion",
                "nomion",
                null
        );
        UserResponse userResponse = new UserResponse(
                userId,
                "Administrador Nomion",
                "admin@nomion.com",
                List.of(new TenantMembershipResponse(
                        membershipId,
                        tenantId,
                        "Nomion",
                        "nomion",
                        TenantMembershipStatus.ACTIVE
                ))
        );

        when(properties.enabled()).thenReturn(true);
        when(properties.token()).thenReturn(SETUP_TOKEN);
        when(stateRepository.findByIdForUpdate(InitialSetupState.SINGLETON_ID))
                .thenReturn(Optional.of(state));
        when(state.isInitialized()).thenReturn(false);
        when(tenantRepository.existsBySlug("nomion")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("admin@nomion.com"))
                .thenReturn(false);
        when(tenantMapper.toEntity(request.tenant())).thenReturn(tenant);
        when(tenantRepository.saveAndFlush(tenant)).thenReturn(tenant);
        when(passwordEncoder.encode("secure-password")).thenReturn("encoded-password");
        when(userMapper.toEntity(
                "Administrador Nomion",
                "admin@nomion.com",
                "encoded-password"
        )).thenReturn(administrator);
        when(userRepository.saveAndFlush(administrator)).thenReturn(administrator);
        when(membershipService.create(administrator, tenant)).thenReturn(membership);
        when(tenantMapper.toResponse(tenant)).thenReturn(tenantResponse);
        when(userMapper.toResponse(administrator, List.of(membership)))
                .thenReturn(userResponse);
        when(state.getInitializedAt()).thenReturn(initializedAt);

        InitialSetupResponse result = service.initialize(SETUP_TOKEN, request);

        assertEquals(
                new InitialSetupResponse(tenantResponse, userResponse, initializedAt),
                result
        );
        verify(roleAssignmentService).assignTenantRoles(
                membership,
                Set.of(RoleCode.TENANT_ADMIN),
                administrator
        );
        verify(roleAssignmentService).assignPlatformRole(
                administrator,
                RoleCode.PLATFORM_ADMIN,
                administrator
        );
        verify(state).markInitialized(administrator);
    }


    @Test
    void shouldHideEndpointWhenSetupIsDisabled() {

        when(properties.enabled()).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                service.initialize(SETUP_TOKEN, request()));

        verifyNoInteractions(stateRepository);
    }


    @Test
    void shouldRejectInvalidSetupToken() {

        when(properties.enabled()).thenReturn(true);
        when(properties.token()).thenReturn(SETUP_TOKEN);

        assertThrows(UnauthorizedException.class, () ->
                service.initialize("invalid-token", request()));

        verifyNoInteractions(stateRepository);
    }


    @Test
    void shouldRejectPasswordLongerThanBcryptByteLimit() {

        InitialSetupRequest request = new InitialSetupRequest(
                new CreateTenantRequest("Nomion", "nomion"),
                new InitialAdminRequest(
                        "Administrador Nomion",
                        "admin@nomion.com",
                        "á".repeat(37)
                )
        );

        when(properties.enabled()).thenReturn(true);
        when(properties.token()).thenReturn(SETUP_TOKEN);

        assertThrows(BusinessException.class, () ->
                service.initialize(SETUP_TOKEN, request));

        verifyNoInteractions(stateRepository);
    }


    @Test
    void shouldRejectSecondInitialization() {

        when(properties.enabled()).thenReturn(true);
        when(properties.token()).thenReturn(SETUP_TOKEN);
        when(stateRepository.findByIdForUpdate(InitialSetupState.SINGLETON_ID))
                .thenReturn(Optional.of(state));
        when(state.isInitialized()).thenReturn(true);

        assertThrows(ResourceNotFoundException.class, () ->
                service.initialize(SETUP_TOKEN, request()));

        verify(tenantRepository, never()).existsBySlug("nomion");
    }


    @Test
    void shouldRejectExistingAdministratorEmail() {

        when(properties.enabled()).thenReturn(true);
        when(properties.token()).thenReturn(SETUP_TOKEN);
        when(stateRepository.findByIdForUpdate(InitialSetupState.SINGLETON_ID))
                .thenReturn(Optional.of(state));
        when(state.isInitialized()).thenReturn(false);
        when(tenantRepository.existsBySlug("nomion")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("admin@nomion.com"))
                .thenReturn(true);

        assertThrows(BusinessException.class, () ->
                service.initialize(SETUP_TOKEN, request()));

        verify(tenantRepository, never()).saveAndFlush(
                org.mockito.ArgumentMatchers.any()
        );
    }


    private InitialSetupRequest request() {

        return new InitialSetupRequest(
                new CreateTenantRequest("Nomion", "nomion"),
                new InitialAdminRequest(
                        "Administrador Nomion",
                        "ADMIN@NOMION.COM",
                        "secure-password"
                )
        );
    }
}
