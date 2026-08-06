package com.nomion.publicentity.service;

import com.nomion.handler.BusinessException;
import com.nomion.handler.ResourceNotFoundException;
import com.nomion.handler.UnauthorizedException;
import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.publicentity.domain.PublicEntityType;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicEntityServiceTest {


    @Mock
    private PublicEntityRepository repository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PublicEntityMapper mapper;

    @InjectMocks
    private PublicEntityService service;


    @Test
    void shouldCreateEntityInAuthenticatedTenantWithNormalizedCnpj() {

        UUID tenantId = UUID.randomUUID();
        CreatePublicEntityRequest request = createRequest();
        Tenant tenant = Tenant.builder().id(tenantId).build();
        PublicEntity entity = PublicEntity.builder().tenant(tenant).build();
        PublicEntityResponse response = response(UUID.randomUUID(), true);

        when(repository.existsByTenant_IdAndCnpj(
                tenantId,
                "11222333000181"
        )).thenReturn(false);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(mapper.toEntity(request, tenant, "11222333000181"))
                .thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toResponse(entity)).thenReturn(response);

        assertEquals(response, service.create(request, authenticatedUser(tenantId)));

        verify(repository).saveAndFlush(entity);
    }


    @Test
    void shouldRejectDuplicateCnpjInSameTenant() {

        UUID tenantId = UUID.randomUUID();

        when(repository.existsByTenant_IdAndCnpj(
                tenantId,
                "11222333000181"
        )).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.create(
                createRequest(),
                authenticatedUser(tenantId)
        ));

        verify(tenantRepository, never()).findById(any());
        verify(repository, never()).saveAndFlush(any());
    }


    @Test
    void shouldHideEntityFromAnotherTenant() {

        UUID entityId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(repository.findByIdAndTenant_Id(entityId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(
                entityId,
                authenticatedUser(tenantId)
        ));

        verify(mapper, never()).toResponse(any());
    }


    @Test
    void shouldUpdateEntityAndCheckCnpjExcludingCurrentId() {

        UUID entityId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        PublicEntity entity = PublicEntity.builder().id(entityId).build();
        UpdatePublicEntityRequest request = updateRequest();
        PublicEntityResponse response = response(entityId, false);

        when(repository.findByIdAndTenant_Id(entityId, tenantId))
                .thenReturn(Optional.of(entity));
        when(repository.existsByTenant_IdAndCnpjAndIdNot(
                tenantId,
                "11222333000181",
                entityId
        )).thenReturn(false);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toResponse(entity)).thenReturn(response);

        assertEquals(response, service.update(
                entityId,
                request,
                authenticatedUser(tenantId)
        ));

        verify(mapper).update(entity, request, "11222333000181");
    }


    @Test
    void shouldListOnlyAuthenticatedTenantWithNormalizedFilters() {

        UUID tenantId = UUID.randomUUID();
        PublicEntity entity = PublicEntity.builder().build();
        PublicEntityResponse response = response(UUID.randomUUID(), true);
        PublicEntityFilter filter = new PublicEntityFilter(
                " prefeitura ",
                "11.222.333/0001-81",
                PublicEntityType.CITY_HALL,
                " campinas ",
                "sp",
                true
        );
        PageRequest pageable = PageRequest.of(
                0,
                20,
                Sort.by("name").ascending().and(Sort.by("id").ascending())
        );

        when(repository.search(
                tenantId,
                "prefeitura",
                "11222333000181",
                PublicEntityType.CITY_HALL,
                "campinas",
                "SP",
                true,
                pageable
        )).thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
        when(mapper.toResponse(entity)).thenReturn(response);

        PublicEntityPageResponse result = service.findAll(
                filter,
                0,
                20,
                authenticatedUser(tenantId)
        );

        assertEquals(List.of(response), result.content());
        assertEquals(1, result.totalElements());
    }


    @Test
    void shouldRejectInvalidCnpjFilter() {

        assertThrows(BusinessException.class, () -> service.findAll(
                new PublicEntityFilter(null, "123", null, null, null, null),
                0,
                20,
                authenticatedUser(UUID.randomUUID())
        ));

        verify(repository, never()).search(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }


    @Test
    void shouldRequireAuthentication() {

        assertThrows(UnauthorizedException.class, () -> service.findById(
                UUID.randomUUID(),
                null
        ));

        verify(repository, never()).findByIdAndTenant_Id(any(), any());
    }


    private CreatePublicEntityRequest createRequest() {

        return new CreatePublicEntityRequest(
                "Prefeitura Municipal",
                "11.222.333/0001-81",
                PublicEntityType.CITY_HALL,
                "Campinas",
                "SP",
                "contato@example.com",
                "(19) 99999-9999"
        );
    }


    private UpdatePublicEntityRequest updateRequest() {

        return new UpdatePublicEntityRequest(
                "Prefeitura Municipal Atualizada",
                "11.222.333/0001-81",
                PublicEntityType.CITY_HALL,
                "Campinas",
                "SP",
                "novo@example.com",
                "(19) 98888-8888",
                false
        );
    }


    private PublicEntityResponse response(UUID id, boolean active) {

        Instant now = Instant.now();

        return new PublicEntityResponse(
                id,
                "Prefeitura Municipal",
                "11222333000181",
                PublicEntityType.CITY_HALL,
                "Campinas",
                "SP",
                "contato@example.com",
                "(19) 99999-9999",
                active,
                now,
                now
        );
    }


    private AuthenticatedUser authenticatedUser(UUID tenantId) {

        return new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId,
                "admin@nomion.com",
                Set.of("TENANT_ADMIN"),
                Set.of(
                        "PUBLIC_ENTITY_CREATE",
                        "PUBLIC_ENTITY_READ",
                        "PUBLIC_ENTITY_UPDATE"
                )
        );
    }
}
