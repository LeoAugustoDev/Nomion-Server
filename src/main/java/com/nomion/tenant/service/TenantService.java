package com.nomion.tenant.service;

import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.repository.TenantRepository;
import com.nomion.tenant.dto.CreateTenantRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantService {


    private final TenantRepository repository;


    public TenantService(TenantRepository repository) {
        this.repository = repository;
    }


    @Transactional
    public Tenant create(CreateTenantRequest request){

        if(repository.existsBySlug(request.slug())){
            throw new RuntimeException("Slug já utilizado");
        }


        Tenant tenant = Tenant.builder()
                .name(request.name())
                .slug(request.slug())
                .build();


        return repository.save(tenant);
    }


    public Tenant findById(UUID id){

        return repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Tenant não encontrado"));
    }

}