package com.nomion.security;

import com.nomion.user.domain.MembershipRoleAssignment;
import com.nomion.user.domain.Permission;
import com.nomion.user.domain.Role;
import com.nomion.user.domain.RoleScope;
import com.nomion.user.domain.UserPlatformRoleAssignment;
import com.nomion.user.repository.MembershipRoleAssignmentRepository;
import com.nomion.user.repository.PermissionRepository;
import com.nomion.user.repository.UserPlatformRoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {


    @Mock
    private MembershipRoleAssignmentRepository membershipRoleRepository;

    @Mock
    private UserPlatformRoleAssignmentRepository platformRoleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private AuthorizationService service;


    @Test
    void shouldCombineActiveTenantAndPlatformRolesAndPermissions() {

        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        Role tenantRole = role("TENANT_ADMIN", RoleScope.TENANT, true);
        Role inactiveRole = role("VIEWER", RoleScope.TENANT, false);
        Role invalidMembershipRole = role("PLATFORM_ADMIN", RoleScope.PLATFORM, true);
        Role platformRole = role("PLATFORM_ADMIN", RoleScope.PLATFORM, true);
        Role invalidPlatformRole = role("VIEWER", RoleScope.TENANT, true);

        when(membershipRoleRepository.findAllByMembership_Id(membershipId))
                .thenReturn(List.of(
                        MembershipRoleAssignment.builder().role(tenantRole).build(),
                        MembershipRoleAssignment.builder().role(inactiveRole).build(),
                        MembershipRoleAssignment.builder().role(invalidMembershipRole).build()
                ));
        when(platformRoleRepository.findAllByUser_Id(userId))
                .thenReturn(List.of(
                        UserPlatformRoleAssignment.builder().role(platformRole).build(),
                        UserPlatformRoleAssignment.builder().role(invalidPlatformRole).build()
                ));
        when(permissionRepository.findAllByRoleIds(Set.of(
                tenantRole.getId(),
                platformRole.getId()
        ))).thenReturn(List.of(
                permission("TENANT_READ"),
                permission("USER_READ"),
                permission("TENANT_READ")
        ));

        AuthorizationContext context = service.load(userId, membershipId);

        assertEquals(
                Set.of("PLATFORM_ADMIN", "TENANT_ADMIN"),
                context.roles()
        );
        assertEquals(
                Set.of("TENANT_READ", "USER_READ"),
                context.permissions()
        );
    }


    @Test
    void shouldReturnEmptyContextWhenUserHasNoActiveRoles() {

        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        when(membershipRoleRepository.findAllByMembership_Id(membershipId))
                .thenReturn(List.of());
        when(platformRoleRepository.findAllByUser_Id(userId))
                .thenReturn(List.of());

        AuthorizationContext context = service.load(userId, membershipId);

        assertEquals(Set.of(), context.roles());
        assertEquals(Set.of(), context.permissions());
        verify(permissionRepository, never()).findAllByRoleIds(anySet());
    }


    private Role role(String code, RoleScope scope, boolean active) {

        return Role.builder()
                .id(UUID.randomUUID())
                .code(code)
                .scope(scope)
                .active(active)
                .build();
    }


    private Permission permission(String code) {

        return Permission.builder()
                .id(UUID.randomUUID())
                .code(code)
                .build();
    }
}
