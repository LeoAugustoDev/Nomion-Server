package com.nomion.tenant.service;

import com.nomion.handler.BusinessException;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.repository.TenantMembershipRepository;
import com.nomion.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantMembershipServiceTest {


    @Mock
    private TenantMembershipRepository repository;

    @InjectMocks
    private TenantMembershipService service;


    @Test
    void shouldCreateActiveMembership() {

        User user = User.builder().id(UUID.randomUUID()).build();
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).build();

        when(repository.existsByUser_IdAndTenant_Id(user.getId(), tenant.getId()))
                .thenReturn(false);
        when(repository.saveAndFlush(any(TenantMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TenantMembership membership = service.create(user, tenant);

        assertEquals(user, membership.getUser());
        assertEquals(tenant, membership.getTenant());
        assertEquals(TenantMembershipStatus.ACTIVE, membership.getStatus());
    }


    @Test
    void shouldRejectDuplicateMembership() {

        User user = User.builder().id(UUID.randomUUID()).build();
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).build();

        when(repository.existsByUser_IdAndTenant_Id(user.getId(), tenant.getId()))
                .thenReturn(true);

        assertThrows(BusinessException.class, () -> service.create(user, tenant));

        verify(repository, never()).saveAndFlush(any());
    }


    @Test
    void shouldReturnActiveMembershipsOrderedByTenantName() {

        UUID userId = UUID.randomUUID();
        TenantMembership second = membership("Zeta");
        TenantMembership first = membership("Alfa");

        when(repository.findAllByUser_IdAndStatus(
                userId,
                TenantMembershipStatus.ACTIVE
        )).thenReturn(List.of(second, first));

        List<TenantMembership> memberships = service.findActiveByUser(userId);

        assertEquals(List.of(first, second), memberships);
    }

    private TenantMembership membership(String tenantName) {

        return TenantMembership.builder()
                .tenant(Tenant.builder().name(tenantName).build())
                .status(TenantMembershipStatus.ACTIVE)
                .build();
    }
}
