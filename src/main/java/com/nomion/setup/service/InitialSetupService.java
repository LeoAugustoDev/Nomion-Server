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
import com.nomion.tenant.mapper.TenantMapper;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.tenant.service.TenantMembershipService;
import com.nomion.user.domain.RoleCode;
import com.nomion.user.domain.User;
import com.nomion.user.mapper.UserMapper;
import com.nomion.user.repository.UserRepository;
import com.nomion.user.service.RoleAssignmentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class InitialSetupService {


    private static final String SETUP_UNAVAILABLE_MESSAGE =
            "Setup inicial não disponível";


    private final InitialSetupProperties properties;
    private final InitialSetupStateRepository stateRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantMembershipService membershipService;
    private final RoleAssignmentService roleAssignmentService;
    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;


    public InitialSetupService(
            InitialSetupProperties properties,
            InitialSetupStateRepository stateRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            TenantMembershipService membershipService,
            RoleAssignmentService roleAssignmentService,
            TenantMapper tenantMapper,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.stateRepository = stateRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.membershipService = membershipService;
        this.roleAssignmentService = roleAssignmentService;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }


    @Transactional
    public InitialSetupResponse initialize(
            String providedToken,
            InitialSetupRequest request
    ) {

        validateAvailability();
        validateToken(providedToken);
        validatePassword(request.administrator().password());

        InitialSetupState state = stateRepository.findByIdForUpdate(
                        InitialSetupState.SINGLETON_ID
                )
                .orElseThrow(() ->
                        new IllegalStateException("Estado do setup inicial ausente"));

        if (state.isInitialized()) {
            throw new ResourceNotFoundException(SETUP_UNAVAILABLE_MESSAGE);
        }

        String email = normalizeEmail(request.administrator().email());

        validateUniqueData(request.tenant().slug(), email);

        Tenant tenant = createTenant(request);
        User administrator = createAdministrator(request.administrator(), email);
        TenantMembership membership = membershipService.create(administrator, tenant);

        roleAssignmentService.assignTenantRoles(
                membership,
                Set.of(RoleCode.TENANT_ADMIN),
                administrator
        );
        roleAssignmentService.assignPlatformRole(
                administrator,
                RoleCode.PLATFORM_ADMIN,
                administrator
        );

        state.markInitialized(administrator);

        return new InitialSetupResponse(
                tenantMapper.toResponse(tenant),
                userMapper.toResponse(administrator, List.of(membership)),
                state.getInitializedAt()
        );
    }


    private void validateAvailability() {

        if (!properties.enabled()) {
            throw new ResourceNotFoundException(SETUP_UNAVAILABLE_MESSAGE);
        }
    }


    private void validateToken(String providedToken) {

        byte[] expected = properties.token().getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedToken == null
                ? new byte[0]
                : providedToken.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expected, provided)) {
            throw new UnauthorizedException("Credencial de setup inválida");
        }
    }


    private void validateUniqueData(String slug, String email) {

        if (tenantRepository.existsBySlug(slug)) {
            throw new BusinessException("Slug já utilizado");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("E-mail já utilizado");
        }
    }


    private void validatePassword(String password) {

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    "Senha do administrador deve possuir no máximo 72 bytes em UTF-8"
            );
        }
    }


    private Tenant createTenant(InitialSetupRequest request) {

        try {
            return tenantRepository.saveAndFlush(
                    tenantMapper.toEntity(request.tenant())
            );
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Slug já utilizado");
        }
    }


    private User createAdministrator(
            InitialAdminRequest request,
            String email
    ) {

        User administrator = userMapper.toEntity(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password())
        );

        try {
            return userRepository.saveAndFlush(administrator);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("E-mail já utilizado");
        }
    }


    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
