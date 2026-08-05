package com.nomion.tenant.service;

import com.nomion.handler.ResourceNotFoundException;
import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.dto.TenantResponse;
import com.nomion.tenant.mapper.TenantMapper;
import com.nomion.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {


    @Mock
    private TenantRepository repository;

    @Mock
    private TenantMapper mapper;

    @InjectMocks
    private TenantService service;


    @Test
    void shouldReturnOnlyAuthenticatedTenant() {

        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).build();
        TenantResponse response = new TenantResponse(tenantId, "Nomion", "nomion", null);
        AuthenticatedUser authenticatedUser = authenticatedUser(tenantId);

        when(repository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(mapper.toResponse(tenant)).thenReturn(response);

        TenantResponse result = service.findById(tenantId, authenticatedUser);

        assertEquals(response, result);
    }


    @Test
    void shouldHideAnotherTenant() {

        UUID requestedTenantId = UUID.randomUUID();
        AuthenticatedUser authenticatedUser = authenticatedUser(UUID.randomUUID());

        assertThrows(ResourceNotFoundException.class, () ->
                service.findById(requestedTenantId, authenticatedUser));

        verify(repository, never()).findById(requestedTenantId);
    }


    private AuthenticatedUser authenticatedUser(UUID tenantId) {

        return new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId,
                "admin@nomion.com",
                Set.of("TENANT_ADMIN"),
                Set.of("TENANT_READ")
        );
    }
}
