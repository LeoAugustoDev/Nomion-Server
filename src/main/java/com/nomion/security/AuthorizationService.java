package com.nomion.security;

import com.nomion.user.domain.Permission;
import com.nomion.user.domain.Role;
import com.nomion.user.domain.RoleScope;
import com.nomion.user.repository.MembershipRoleAssignmentRepository;
import com.nomion.user.repository.PermissionRepository;
import com.nomion.user.repository.UserPlatformRoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthorizationService {


    private final MembershipRoleAssignmentRepository membershipRoleRepository;
    private final UserPlatformRoleAssignmentRepository platformRoleRepository;
    private final PermissionRepository permissionRepository;


    public AuthorizationService(
            MembershipRoleAssignmentRepository membershipRoleRepository,
            UserPlatformRoleAssignmentRepository platformRoleRepository,
            PermissionRepository permissionRepository
    ) {
        this.membershipRoleRepository = membershipRoleRepository;
        this.platformRoleRepository = platformRoleRepository;
        this.permissionRepository = permissionRepository;
    }


    @Transactional(readOnly = true)
    public AuthorizationContext load(UUID userId, UUID membershipId) {

        Set<Role> roles = membershipRoleRepository.findAllByMembership_Id(membershipId)
                .stream()
                .map(assignment -> assignment.getRole())
                .filter(role -> role.isActive() && role.getScope() == RoleScope.TENANT)
                .collect(Collectors.toSet());

        platformRoleRepository.findAllByUser_Id(userId)
                .stream()
                .map(assignment -> assignment.getRole())
                .filter(role -> role.isActive() && role.getScope() == RoleScope.PLATFORM)
                .forEach(roles::add);

        Set<UUID> roleIds = roles.stream()
                .map(Role::getId)
                .collect(Collectors.toSet());

        List<Permission> permissions = roleIds.isEmpty()
                ? List.of()
                : permissionRepository.findAllByRoleIds(roleIds);

        return new AuthorizationContext(
                roles.stream()
                        .map(Role::getCode)
                        .collect(Collectors.toCollection(TreeSet::new)),
                permissions.stream()
                        .map(Permission::getCode)
                        .collect(Collectors.toCollection(TreeSet::new))
        );
    }
}
