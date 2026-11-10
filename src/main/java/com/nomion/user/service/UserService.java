package com.nomion.user.service;

import com.nomion.handler.BusinessException;
import com.nomion.handler.ResourceNotFoundException;
import com.nomion.handler.UnauthorizedException;
import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.tenant.service.TenantMembershipService;
import com.nomion.user.domain.User;
import com.nomion.user.dto.CreateUserRequest;
import com.nomion.user.dto.CurrentUserResponse;
import com.nomion.user.dto.UserResponse;
import com.nomion.user.mapper.UserMapper;
import com.nomion.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {


    private final UserRepository repository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipService membershipService;
    private final RoleAssignmentService roleAssignmentService;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;


    public UserService(
            UserRepository repository,
            TenantRepository tenantRepository,
            TenantMembershipService membershipService,
            RoleAssignmentService roleAssignmentService,
            UserMapper mapper,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.membershipService = membershipService;
        this.roleAssignmentService = roleAssignmentService;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }


    @Transactional
    public UserResponse create(
            CreateUserRequest request,
            AuthenticatedUser authenticatedUser
    ) {

        if (authenticatedUser == null) {
            throw new UnauthorizedException("Autenticação necessária");
        }

        String email = normalizeEmail(request.email());

        if (repository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("E-mail já utilizado");
        }


        Tenant tenant = tenantRepository.findById(authenticatedUser.tenantId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant não encontrado"));


        User user = mapper.toEntity(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password())
        );

        User savedUser;

        try {
            savedUser = repository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("E-mail já utilizado");
        }

        TenantMembership membership = membershipService.create(savedUser, tenant);
        User assignedBy = repository.getReferenceById(authenticatedUser.id());

        roleAssignmentService.assignTenantRoles(
                membership,
                request.roles(),
                assignedBy
        );

        return mapper.toResponse(savedUser, List.of(membership));
    }


    @Transactional(readOnly = true)
    public UserResponse findById(UUID id, AuthenticatedUser authenticatedUser) {

        if (authenticatedUser == null) {
            throw new UnauthorizedException("Autenticação necessária");
        }

        User user = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Usuário não encontrado"));

        TenantMembership membership = membershipService.findActive(
                        id,
                        authenticatedUser.tenantId()
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException("Usuário não encontrado"));

        return mapper.toResponse(user, List.of(membership));
    }


    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(AuthenticatedUser authenticatedUser) {
        User user = repository.findById(authenticatedUser.id())
                .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));
        UserResponse profile = mapper.toResponse(user, membershipService.findActiveByUser(user.getId()));

        return new CurrentUserResponse(
                profile, authenticatedUser.tenantId(),
                authenticatedUser.roles(), authenticatedUser.permissions()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
