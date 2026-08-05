package com.nomion.user.service;

import com.nomion.handler.BusinessException;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.user.domain.MembershipRoleAssignment;
import com.nomion.user.domain.MembershipRoleAssignmentId;
import com.nomion.user.domain.Role;
import com.nomion.user.domain.RoleCode;
import com.nomion.user.domain.RoleScope;
import com.nomion.user.domain.User;
import com.nomion.user.domain.UserPlatformRoleAssignment;
import com.nomion.user.domain.UserPlatformRoleAssignmentId;
import com.nomion.user.repository.MembershipRoleAssignmentRepository;
import com.nomion.user.repository.RoleRepository;
import com.nomion.user.repository.UserPlatformRoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoleAssignmentService {


    private final RoleRepository roleRepository;
    private final MembershipRoleAssignmentRepository membershipRoleRepository;
    private final UserPlatformRoleAssignmentRepository platformRoleRepository;


    public RoleAssignmentService(
            RoleRepository roleRepository,
            MembershipRoleAssignmentRepository membershipRoleRepository,
            UserPlatformRoleAssignmentRepository platformRoleRepository
    ) {
        this.roleRepository = roleRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.platformRoleRepository = platformRoleRepository;
    }


    @Transactional
    public void assignTenantRoles(
            TenantMembership membership,
            Set<RoleCode> roleCodes,
            User assignedBy
    ) {

        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BusinessException("Ao menos um perfil de tenant é obrigatório");
        }

        if (roleCodes.contains(RoleCode.PLATFORM_ADMIN)) {
            throw new BusinessException("Perfil de plataforma inválido para o tenant");
        }

        Set<String> codes = roleCodes.stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        List<Role> roles = roleRepository.findAssignableTenantRoles(
                codes,
                RoleScope.TENANT,
                membership.getTenant().getId()
        );

        if (roles.size() != codes.size()) {
            throw new BusinessException("Um ou mais perfis são inválidos para o tenant");
        }

        List<MembershipRoleAssignment> assignments = roles.stream()
                .map(role -> MembershipRoleAssignment.builder()
                        .id(new MembershipRoleAssignmentId(
                                membership.getId(),
                                role.getId()
                        ))
                        .membership(membership)
                        .role(role)
                        .assignedBy(assignedBy)
                        .build())
                .toList();

        membershipRoleRepository.saveAll(assignments);
    }


    @Transactional
    public void assignPlatformRole(
            User user,
            RoleCode roleCode,
            User assignedBy
    ) {

        Role role = roleRepository
                .findByCodeAndScopeAndTenantIsNullAndActiveTrue(
                        roleCode.name(),
                        RoleScope.PLATFORM
                )
                .orElseThrow(() ->
                        new BusinessException("Perfil de plataforma inválido"));

        platformRoleRepository.save(UserPlatformRoleAssignment.builder()
                .id(new UserPlatformRoleAssignmentId(user.getId(), role.getId()))
                .user(user)
                .role(role)
                .assignedBy(assignedBy)
                .build());
    }
}
