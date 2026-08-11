package com.nomion.contract;

import com.nomion.security.AuthorizationContext;
import com.nomion.security.AuthorizationService;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.repository.TenantMembershipRepository;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.user.domain.RoleCode;
import com.nomion.user.domain.User;
import com.nomion.user.repository.UserRepository;
import com.nomion.user.service.RoleAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ContractPermissionIntegrationTest {


    private static final Set<String> ALL_PERMISSIONS = Set.of(
            "CONTRACT_CREATE",
            "CONTRACT_READ",
            "CONTRACT_UPDATE"
    );
    private static final Set<String> READ_PERMISSION = Set.of("CONTRACT_READ");


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
    void shouldResolveContractPermissionMatrixFromSeededRoles() {

        assertTenantRolePermissions(RoleCode.TENANT_ADMIN, ALL_PERMISSIONS);
        assertTenantRolePermissions(RoleCode.CONSULTANT, ALL_PERMISSIONS);
        assertTenantRolePermissions(RoleCode.ANALYST, READ_PERMISSION);
        assertTenantRolePermissions(RoleCode.VIEWER, READ_PERMISSION);

        TenantMembership platformMembership = createMembership("platform-admin");
        User platformUser = platformMembership.getUser();

        roleAssignmentService.assignPlatformRole(
                platformUser,
                RoleCode.PLATFORM_ADMIN,
                platformUser
        );

        assertEquals(
                ALL_PERMISSIONS,
                contractPermissions(authorizationService.load(
                        platformUser.getId(),
                        platformMembership.getId()
                ))
        );
    }


    private void assertTenantRolePermissions(
            RoleCode roleCode,
            Set<String> expectedPermissions
    ) {

        TenantMembership membership = createMembership(roleCode.name().toLowerCase());

        roleAssignmentService.assignTenantRoles(
                membership,
                Set.of(roleCode),
                membership.getUser()
        );

        assertEquals(
                expectedPermissions,
                contractPermissions(authorizationService.load(
                        membership.getUser().getId(),
                        membership.getId()
                ))
        );
    }


    private TenantMembership createMembership(String suffix) {

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Tenant " + suffix)
                .slug("contract-permission-" + suffix + "-" + UUID.randomUUID())
                .build());
        User user = userRepository.save(User.builder()
                .name("User " + suffix)
                .email("contract-permission-" + suffix + "-" + UUID.randomUUID()
                        + "@nomion.com")
                .password("encoded-password")
                .build());

        return membershipRepository.saveAndFlush(TenantMembership.builder()
                .tenant(tenant)
                .user(user)
                .status(TenantMembershipStatus.ACTIVE)
                .build());
    }


    private Set<String> contractPermissions(AuthorizationContext context) {

        return context.permissions()
                .stream()
                .filter(permission -> permission.startsWith("CONTRACT_"))
                .collect(Collectors.toSet());
    }
}
