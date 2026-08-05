package com.nomion.tenant.service;

import com.nomion.handler.BusinessException;
import com.nomion.handler.ResourceNotFoundException;
import com.nomion.handler.UnauthorizedException;
import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.dto.CreateTenantRequest;
import com.nomion.tenant.dto.TenantResponse;
import com.nomion.tenant.mapper.TenantMapper;
import com.nomion.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantService {


    private final TenantRepository repository;
    private final TenantMapper mapper;


    public TenantService(TenantRepository repository, TenantMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }


    @Transactional
    public TenantResponse create(CreateTenantRequest request){

        if(repository.existsBySlug(request.slug())){
            throw new BusinessException("Slug já utilizado");
        }


        Tenant tenant = mapper.toEntity(request);


        return mapper.toResponse(repository.save(tenant));
    }


    @Transactional(readOnly = true)
    public TenantResponse findById(UUID id, AuthenticatedUser authenticatedUser){

        if (authenticatedUser == null) {
            throw new UnauthorizedException("Autenticação necessária");
        }

        if (!id.equals(authenticatedUser.tenantId())) {
            throw new ResourceNotFoundException("Tenant não encontrado");
        }

        Tenant tenant = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant não encontrado"));

        return mapper.toResponse(tenant);
    }

}
