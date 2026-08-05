package com.nomion.tenant.service;

import com.nomion.handler.ResourceNotFoundException;
import com.nomion.handler.UnauthorizedException;
import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.dto.CreateTenantMembershipRequest;
import com.nomion.tenant.dto.TenantMembershipResponse;
import com.nomion.tenant.mapper.TenantMembershipMapper;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.user.domain.RoleCode;
import com.nomion.user.domain.User;
import com.nomion.user.repository.UserRepository;
import com.nomion.user.service.RoleAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantMembershipManagementService {


    private final TenantMembershipService membershipService;
    private final TenantMembershipMapper membershipMapper;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleAssignmentService roleAssignmentService;


    public TenantMembershipManagementService(
            TenantMembershipService membershipService,
            TenantMembershipMapper membershipMapper,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleAssignmentService roleAssignmentService
    ) {
        this.membershipService = membershipService;
        this.membershipMapper = membershipMapper;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleAssignmentService = roleAssignmentService;
    }


    @Transactional
    public TenantMembershipResponse create(
            UUID tenantId,
            CreateTenantMembershipRequest request,
            AuthenticatedUser authenticatedUser
    ) {

        if (authenticatedUser == null) {
            throw new UnauthorizedException("Autenticação necessária");
        }

        if (!authenticatedUser.roles().contains(RoleCode.PLATFORM_ADMIN.name())
                && !tenantId.equals(authenticatedUser.tenantId())) {
            throw new ResourceNotFoundException("Tenant não encontrado");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant não encontrado"));

        User user = userRepository.findById(request.userId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Usuário não encontrado"));

        TenantMembership membership = membershipService.create(user, tenant);
        User assignedBy = userRepository.getReferenceById(authenticatedUser.id());

        roleAssignmentService.assignTenantRoles(
                membership,
                request.roles(),
                assignedBy
        );

        return membershipMapper.toResponse(membership);
    }
}
