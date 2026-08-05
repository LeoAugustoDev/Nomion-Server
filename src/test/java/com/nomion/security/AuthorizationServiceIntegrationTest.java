package com.nomion.security;

import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.repository.TenantMembershipRepository;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.user.domain.PermissionCode;
import com.nomion.user.domain.RoleCode;
import com.nomion.user.domain.User;
import com.nomion.user.repository.UserRepository;
import com.nomion.user.service.RoleAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class AuthorizationServiceIntegrationTest {


    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantMembershipRepository membershipRepository;


    @Test
    @Transactional
    void shouldResolveSeededRolesAndPermissionsFromDatabaseAssignments() {

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Tenant RBAC")
                .slug("tenant-rbac-" + UUID.randomUUID())
                .build());
        User user = userRepository.save(User.builder()
                .name("Administrador RBAC")
                .email("admin-rbac-" + UUID.randomUUID() + "@nomion.com")
                .password("encoded-password")
                .build());
        TenantMembership membership = membershipRepository.saveAndFlush(
                TenantMembership.builder()
                        .user(user)
                        .tenant(tenant)
                        .status(TenantMembershipStatus.ACTIVE)
                        .build()
        );

        roleAssignmentService.assignTenantRoles(
                membership,
                Set.of(RoleCode.TENANT_ADMIN),
                user
        );
        roleAssignmentService.assignPlatformRole(
                user,
                RoleCode.PLATFORM_ADMIN,
                user
        );

        AuthorizationContext context = authorizationService.load(
                user.getId(),
                membership.getId()
        );

        assertEquals(
                Set.of("PLATFORM_ADMIN", "TENANT_ADMIN"),
                context.roles()
        );
        assertEquals(
                Arrays.stream(PermissionCode.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()),
                context.permissions()
        );
    }
}
