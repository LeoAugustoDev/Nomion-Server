package com.nomion.tenant.service;

import com.nomion.handler.ResourceNotFoundException;
import com.nomion.handler.UnauthorizedException;
import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.dto.CreateTenantMembershipRequest;
import com.nomion.tenant.dto.TenantMembershipResponse;
import com.nomion.tenant.mapper.TenantMembershipMapper;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.user.domain.RoleCode;
import com.nomion.user.domain.User;
import com.nomion.user.repository.UserRepository;
import com.nomion.user.service.RoleAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantMembershipManagementServiceTest {


    @Mock
    private TenantMembershipService membershipService;

    @Mock
    private TenantMembershipMapper membershipMapper;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleAssignmentService roleAssignmentService;

    @InjectMocks
    private TenantMembershipManagementService service;


    @Test
    void shouldCreateMembershipInAuthenticatedTenant() {

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Nomion")
                .slug("nomion")
                .build();
        User user = User.builder().id(userId).build();
        AuthenticatedUser authenticatedUser = authenticatedUser(
                tenantId,
                Set.of(RoleCode.TENANT_ADMIN.name())
        );
        User assignedBy = User.builder().id(authenticatedUser.id()).build();
        CreateTenantMembershipRequest request = new CreateTenantMembershipRequest(
                userId,
                Set.of(RoleCode.CONSULTANT)
        );
        TenantMembership membership = TenantMembership.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tenant(tenant)
                .status(TenantMembershipStatus.ACTIVE)
                .build();
        TenantMembershipResponse response = new TenantMembershipResponse(
                membership.getId(),
                tenantId,
                "Nomion",
                "nomion",
                TenantMembershipStatus.ACTIVE
        );

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipService.create(user, tenant)).thenReturn(membership);
        when(userRepository.getReferenceById(authenticatedUser.id())).thenReturn(assignedBy);
        when(membershipMapper.toResponse(membership)).thenReturn(response);

        TenantMembershipResponse result = service.create(
                tenantId,
                request,
                authenticatedUser
        );

        assertEquals(response, result);
        verify(roleAssignmentService).assignTenantRoles(
                membership,
                Set.of(RoleCode.CONSULTANT),
                assignedBy
        );
    }


    @Test
    void shouldHideAnotherTenantFromTenantAdministrator() {

        UUID tenantId = UUID.randomUUID();
        UUID requestedTenantId = UUID.randomUUID();
        CreateTenantMembershipRequest request = new CreateTenantMembershipRequest(
                UUID.randomUUID(),
                Set.of(RoleCode.VIEWER)
        );

        assertThrows(ResourceNotFoundException.class, () -> service.create(
                requestedTenantId,
                request,
                authenticatedUser(tenantId, Set.of(RoleCode.TENANT_ADMIN.name()))
        ));

        verify(tenantRepository, never()).findById(requestedTenantId);
        verify(userRepository, never()).findById(request.userId());
    }


    @Test
    void shouldAllowPlatformAdministratorToManageAnotherTenant() {

        UUID activeTenantId = UUID.randomUUID();
        UUID requestedTenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(requestedTenantId).build();
        User user = User.builder().id(userId).build();
        AuthenticatedUser authenticatedUser = authenticatedUser(
                activeTenantId,
                Set.of(RoleCode.PLATFORM_ADMIN.name())
        );
        User assignedBy = User.builder().id(authenticatedUser.id()).build();
        CreateTenantMembershipRequest request = new CreateTenantMembershipRequest(
                userId,
                Set.of(RoleCode.VIEWER)
        );
        TenantMembership membership = TenantMembership.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .user(user)
                .build();
        TenantMembershipResponse response = new TenantMembershipResponse(
                membership.getId(),
                requestedTenantId,
                null,
                null,
                null
        );

        when(tenantRepository.findById(requestedTenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipService.create(user, tenant)).thenReturn(membership);
        when(userRepository.getReferenceById(authenticatedUser.id())).thenReturn(assignedBy);
        when(membershipMapper.toResponse(membership)).thenReturn(response);

        assertEquals(response, service.create(
                requestedTenantId,
                request,
                authenticatedUser
        ));
    }


    @Test
    void shouldRequireAuthentication() {

        assertThrows(UnauthorizedException.class, () -> service.create(
                UUID.randomUUID(),
                new CreateTenantMembershipRequest(
                        UUID.randomUUID(),
                        Set.of(RoleCode.VIEWER)
                ),
                null
        ));

        verify(tenantRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }


    private AuthenticatedUser authenticatedUser(
            UUID tenantId,
            Set<String> roles
    ) {

        return new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId,
                "admin@nomion.com",
                roles,
                Set.of("MEMBERSHIP_CREATE", "ROLE_ASSIGN")
        );
    }
}
