package com.nomion.user.service;

import com.nomion.handler.BusinessException;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.user.domain.MembershipRoleAssignment;
import com.nomion.user.domain.Role;
import com.nomion.user.domain.RoleCode;
import com.nomion.user.domain.RoleScope;
import com.nomion.user.domain.User;
import com.nomion.user.domain.UserPlatformRoleAssignment;
import com.nomion.user.repository.MembershipRoleAssignmentRepository;
import com.nomion.user.repository.RoleRepository;
import com.nomion.user.repository.UserPlatformRoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {


    @Mock
    private RoleRepository roleRepository;

    @Mock
    private MembershipRoleAssignmentRepository membershipRoleRepository;

    @Mock
    private UserPlatformRoleAssignmentRepository platformRoleRepository;

    @InjectMocks
    private RoleAssignmentService service;


    @Test
    @SuppressWarnings("unchecked")
    void shouldAssignValidTenantRoles() {

        UUID tenantId = UUID.randomUUID();
        TenantMembership membership = membership(tenantId);
        User assignedBy = User.builder().id(UUID.randomUUID()).build();
        Role admin = role(RoleCode.TENANT_ADMIN, RoleScope.TENANT);
        Role viewer = role(RoleCode.VIEWER, RoleScope.TENANT);
        Set<RoleCode> roleCodes = Set.of(RoleCode.TENANT_ADMIN, RoleCode.VIEWER);

        when(roleRepository.findAssignableTenantRoles(
                Set.of("TENANT_ADMIN", "VIEWER"),
                RoleScope.TENANT,
                tenantId
        )).thenReturn(List.of(admin, viewer));

        service.assignTenantRoles(membership, roleCodes, assignedBy);

        ArgumentCaptor<Iterable<MembershipRoleAssignment>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(membershipRoleRepository).saveAll(captor.capture());

        List<MembershipRoleAssignment> assignments = StreamSupport
                .stream(captor.getValue().spliterator(), false)
                .toList();

        assertEquals(2, assignments.size());
        assertEquals(Set.of(admin, viewer), assignments.stream()
                .map(MembershipRoleAssignment::getRole)
                .collect(java.util.stream.Collectors.toSet()));
        assignments.forEach(assignment -> {
            assertEquals(membership, assignment.getMembership());
            assertEquals(assignedBy, assignment.getAssignedBy());
            assertEquals(membership.getId(), assignment.getId().getMembershipId());
            assertEquals(assignment.getRole().getId(), assignment.getId().getRoleId());
        });
    }


    @Test
    void shouldRejectPlatformRoleForTenantMembership() {

        TenantMembership membership = membership(UUID.randomUUID());

        assertThrows(BusinessException.class, () -> service.assignTenantRoles(
                membership,
                Set.of(RoleCode.PLATFORM_ADMIN),
                User.builder().id(UUID.randomUUID()).build()
        ));

        verify(roleRepository, never()).findAssignableTenantRoles(any(), any(), any());
        verify(membershipRoleRepository, never()).saveAll(any());
    }


    @Test
    void shouldRejectEmptyTenantRoles() {

        assertThrows(BusinessException.class, () -> service.assignTenantRoles(
                membership(UUID.randomUUID()),
                Set.of(),
                User.builder().id(UUID.randomUUID()).build()
        ));

        verify(roleRepository, never()).findAssignableTenantRoles(any(), any(), any());
        verify(membershipRoleRepository, never()).saveAll(any());
    }


    @Test
    void shouldAssignPlatformRole() {

        User user = User.builder().id(UUID.randomUUID()).build();
        User assignedBy = User.builder().id(UUID.randomUUID()).build();
        Role role = role(RoleCode.PLATFORM_ADMIN, RoleScope.PLATFORM);

        when(roleRepository.findByCodeAndScopeAndTenantIsNullAndActiveTrue(
                RoleCode.PLATFORM_ADMIN.name(),
                RoleScope.PLATFORM
        )).thenReturn(Optional.of(role));

        service.assignPlatformRole(user, RoleCode.PLATFORM_ADMIN, assignedBy);

        ArgumentCaptor<UserPlatformRoleAssignment> captor =
                ArgumentCaptor.forClass(UserPlatformRoleAssignment.class);
        verify(platformRoleRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(role, captor.getValue().getRole());
        assertEquals(assignedBy, captor.getValue().getAssignedBy());
        assertEquals(user.getId(), captor.getValue().getId().getUserId());
        assertEquals(role.getId(), captor.getValue().getId().getRoleId());
    }


    private TenantMembership membership(UUID tenantId) {

        return TenantMembership.builder()
                .id(UUID.randomUUID())
                .tenant(Tenant.builder().id(tenantId).build())
                .build();
    }


    private Role role(RoleCode code, RoleScope scope) {

        return Role.builder()
                .id(UUID.randomUUID())
                .code(code.name())
                .scope(scope)
                .active(true)
                .build();
    }
}
