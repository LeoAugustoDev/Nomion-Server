package com.nomion.publicentity.service;

import com.nomion.handler.BusinessException;
import com.nomion.handler.ResourceNotFoundException;
import com.nomion.handler.UnauthorizedException;
import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.publicentity.dto.CreatePublicEntityRequest;
import com.nomion.publicentity.dto.PublicEntityFilter;
import com.nomion.publicentity.dto.PublicEntityPageResponse;
import com.nomion.publicentity.dto.PublicEntityResponse;
import com.nomion.publicentity.dto.UpdatePublicEntityRequest;
import com.nomion.publicentity.mapper.PublicEntityMapper;
import com.nomion.publicentity.repository.PublicEntityRepository;
import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.repository.TenantRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class PublicEntityService {


    private final PublicEntityRepository repository;
    private final TenantRepository tenantRepository;
    private final PublicEntityMapper mapper;
    private final PublicEntityDeactivationGuard deactivationGuard;


    public PublicEntityService(
            PublicEntityRepository repository,
            TenantRepository tenantRepository,
            PublicEntityMapper mapper,
            PublicEntityDeactivationGuard deactivationGuard
    ) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.mapper = mapper;
        this.deactivationGuard = deactivationGuard;
    }


    @Transactional
    public PublicEntityResponse create(
            CreatePublicEntityRequest request,
            AuthenticatedUser authenticatedUser
    ) {

        UUID tenantId = requireTenantId(authenticatedUser);
        String cnpj = normalizeCnpj(request.cnpj());

        validateUniqueCnpj(tenantId, cnpj, null);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant não encontrado"));
        PublicEntity entity = mapper.toEntity(request, tenant, cnpj);

        try {
            return mapper.toResponse(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("CNPJ já cadastrado no tenant");
        }
    }


    @Transactional(readOnly = true)
    public PublicEntityResponse findById(
            UUID id,
            AuthenticatedUser authenticatedUser
    ) {

        PublicEntity entity = findInTenant(id, requireTenantId(authenticatedUser));

        return mapper.toResponse(entity);
    }


    @Transactional(readOnly = true)
    public PublicEntityPageResponse findAll(
            PublicEntityFilter filter,
            int page,
            int size,
            AuthenticatedUser authenticatedUser
    ) {

        UUID tenantId = requireTenantId(authenticatedUser);
        String cnpj = normalizeFilterCnpj(filter.cnpj());
        String state = normalizeState(filter.state());
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("name").ascending().and(Sort.by("id").ascending())
        );
        Page<PublicEntityResponse> result = repository.search(
                        tenantId,
                        normalizeTextFilter(filter.name()),
                        cnpj,
                        filter.type(),
                        normalizeTextFilter(filter.municipality()),
                        state,
                        filter.active(),
                        pageable
                )
                .map(mapper::toResponse);

        return PublicEntityPageResponse.from(result);
    }


    @Transactional
    public PublicEntityResponse update(
            UUID id,
            UpdatePublicEntityRequest request,
            AuthenticatedUser authenticatedUser
    ) {

        UUID tenantId = requireTenantId(authenticatedUser);
        PublicEntity entity = findInTenant(id, tenantId);
        String cnpj = normalizeCnpj(request.cnpj());

        validateUniqueCnpj(tenantId, cnpj, id);

        if (entity.isActive() && !request.active()) {
            deactivationGuard.validate(tenantId, id);
        }

        mapper.update(entity, request, cnpj);

        try {
            return mapper.toResponse(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("CNPJ já cadastrado no tenant");
        }
    }


    private PublicEntity findInTenant(UUID id, UUID tenantId) {

        return repository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Órgão público não encontrado"));
    }


    private UUID requireTenantId(AuthenticatedUser authenticatedUser) {

        if (authenticatedUser == null || authenticatedUser.tenantId() == null) {
            throw new UnauthorizedException("Autenticação necessária");
        }

        return authenticatedUser.tenantId();
    }


    private void validateUniqueCnpj(
            UUID tenantId,
            String cnpj,
            UUID ignoredId
    ) {

        if (cnpj == null) {
            return;
        }

        boolean exists = ignoredId == null
                ? repository.existsByTenant_IdAndCnpj(tenantId, cnpj)
                : repository.existsByTenant_IdAndCnpjAndIdNot(
                        tenantId,
                        cnpj,
                        ignoredId
                );

        if (exists) {
            throw new BusinessException("CNPJ já cadastrado no tenant");
        }
    }


    private String normalizeCnpj(String cnpj) {
        return cnpj == null
                ? null
                : cnpj.replaceAll("[^A-Za-z0-9]", "")
                        .toUpperCase(Locale.ROOT);
    }


    private String normalizeFilterCnpj(String cnpj) {

        String value = normalizeFilter(cnpj);

        if (value == null) {
            return "";
        }

        String normalized = normalizeCnpj(value);

        if (normalized.length() != 14) {
            throw new BusinessException("Filtro de CNPJ inválido");
        }

        return normalized;
    }


    private String normalizeState(String state) {

        String value = normalizeFilter(state);

        if (value == null) {
            return "";
        }

        if (!value.matches("(?i)^[a-z]{2}$")) {
            throw new BusinessException("Filtro de UF inválido");
        }

        return value.toUpperCase(Locale.ROOT);
    }


    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }


    private String normalizeTextFilter(String value) {

        String normalized = normalizeFilter(value);

        return normalized == null
                ? ""
                : escapeLikePattern(normalized.toLowerCase(Locale.ROOT));
    }


    private String escapeLikePattern(String value) {

        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
