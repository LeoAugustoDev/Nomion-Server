package com.nomion.user.service;

import com.nomion.handler.BusinessException;
import com.nomion.handler.ResourceNotFoundException;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.user.domain.User;
import com.nomion.user.dto.CreateUserRequest;
import com.nomion.user.dto.UserResponse;
import com.nomion.user.mapper.UserMapper;
import com.nomion.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {


    private final UserRepository repository;
    private final TenantRepository tenantRepository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;


    public UserService(
            UserRepository repository,
            TenantRepository tenantRepository,
            UserMapper mapper,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }


    @Transactional
    public UserResponse create(CreateUserRequest request) {

        String email = normalizeEmail(request.email());

        if (repository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("E-mail já utilizado");
        }


        Tenant tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant não encontrado"));


        User user = mapper.toEntity(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                tenant
        );


        return mapper.toResponse(repository.save(user));
    }


    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {

        User user = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Usuário não encontrado"));

        return mapper.toResponse(user);
    }


    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
