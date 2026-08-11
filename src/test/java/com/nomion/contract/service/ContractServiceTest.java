package com.nomion.contract.service;

import com.nomion.contract.domain.Contract;
import com.nomion.contract.domain.ContractStatus;
import com.nomion.contract.dto.ContractFilter;
import com.nomion.contract.dto.ContractPageResponse;
import com.nomion.contract.dto.ContractResponse;
import com.nomion.contract.dto.CreateContractRequest;
import com.nomion.contract.dto.UpdateContractRequest;
import com.nomion.contract.mapper.ContractMapper;
import com.nomion.contract.repository.ContractRepository;
import com.nomion.handler.BusinessException;
import com.nomion.handler.ResourceNotFoundException;
import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.publicentity.repository.PublicEntityRepository;
import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.repository.TenantMembershipRepository;
import com.nomion.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class ContractServiceTest {


    @Mock
    private ContractRepository repository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PublicEntityRepository publicEntityRepository;

    @Mock
    private TenantMembershipRepository membershipRepository;

    @Mock
    private ContractMapper mapper;

    @InjectMocks
    private ContractService service;


    @Test
    void shouldCreateDraftContractWithoutManager() {

        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).build();
        PublicEntity publicEntity = activePublicEntity();
        CreateContractRequest request = createRequest(ContractStatus.DRAFT, null);
        Contract contract = Contract.builder().build();
        ContractResponse response = response();
        AuthenticatedUser authenticatedUser = authenticatedUser(tenantId);

        when(repository.existsByTenant_IdAndNumber(
                tenantId,
                "CT-001/2026"
        )).thenReturn(false);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(publicEntityRepository.findByIdAndTenant_IdAndActiveTrue(
                request.publicEntityId(),
                tenantId
        )).thenReturn(Optional.of(publicEntity));
        when(mapper.toEntity(
                request,
                tenant,
                publicEntity,
                null,
                "CT-001/2026",
                authenticatedUser.id()
        )).thenReturn(contract);
        when(repository.saveAndFlush(contract)).thenReturn(contract);
        when(mapper.toResponse(contract)).thenReturn(response);

        assertEquals(response, service.create(request, authenticatedUser));
    }


    @Test
    void shouldRequireManagerForActiveContract() {

        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).build();
        PublicEntity publicEntity = activePublicEntity();
        CreateContractRequest request = createRequest(ContractStatus.ACTIVE, null);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(publicEntityRepository.findByIdAndTenant_IdAndActiveTrue(
                request.publicEntityId(),
                tenantId
        )).thenReturn(Optional.of(publicEntity));

        assertThrows(BusinessException.class, () -> service.create(
                request,
                authenticatedUser(tenantId)
        ));

        verify(repository, never()).saveAndFlush(any());
    }


    @Test
    void shouldHidePublicEntityFromAnotherTenant() {

        UUID tenantId = UUID.randomUUID();
        CreateContractRequest request = createRequest(ContractStatus.DRAFT, null);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(
                Tenant.builder().id(tenantId).build()
        ));
        when(publicEntityRepository.findByIdAndTenant_IdAndActiveTrue(
                request.publicEntityId(),
                tenantId
        )).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(
                request,
                authenticatedUser(tenantId)
        ));
    }


    @Test
    void shouldRejectDuplicateNumberBeforeLoadingAssociations() {

        UUID tenantId = UUID.randomUUID();

        when(repository.existsByTenant_IdAndNumber(
                tenantId,
                "CT-001/2026"
        )).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.create(
                createRequest(ContractStatus.DRAFT, null),
                authenticatedUser(tenantId)
        ));

        verify(tenantRepository, never()).findById(any());
    }


    @Test
    void shouldRejectInvalidDateRange() {

        CreateContractRequest request = new CreateContractRequest(
                UUID.randomUUID(),
                null,
                "CT-001/2026",
                "Consultoria técnica",
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1),
                null,
                ContractStatus.DRAFT
        );

        assertThrows(BusinessException.class, () -> service.create(
                request,
                authenticatedUser(UUID.randomUUID())
        ));

        verify(repository, never()).existsByTenant_IdAndNumber(any(), any());
    }


    @Test
    void shouldRejectInvalidStatusTransition() {

        UUID tenantId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        Contract contract = Contract.builder()
                .status(ContractStatus.DRAFT)
                .build();
        UpdateContractRequest request = updateRequest(
                ContractStatus.SUSPENDED,
                UUID.randomUUID()
        );

        when(repository.findByIdAndTenant_Id(contractId, tenantId))
                .thenReturn(Optional.of(contract));

        assertThrows(BusinessException.class, () -> service.update(
                contractId,
                request,
                authenticatedUser(tenantId)
        ));
    }


    @Test
    void shouldRejectAnyUpdateToTerminatedContract() {

        UUID tenantId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        Contract contract = Contract.builder()
                .status(ContractStatus.TERMINATED)
                .build();

        when(repository.findByIdAndTenant_Id(contractId, tenantId))
                .thenReturn(Optional.of(contract));

        assertThrows(BusinessException.class, () -> service.update(
                contractId,
                updateRequest(ContractStatus.TERMINATED, UUID.randomUUID()),
                authenticatedUser(tenantId)
        ));
    }


    @Test
    void shouldActivateContractWithActiveManagerAndAuditActor() {

        UUID tenantId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        PublicEntity publicEntity = activePublicEntity();
        TenantMembership manager = TenantMembership.builder()
                .id(UUID.randomUUID())
                .status(TenantMembershipStatus.ACTIVE)
                .build();
        Contract contract = Contract.builder()
                .id(contractId)
                .status(ContractStatus.DRAFT)
                .publicEntity(publicEntity)
                .build();
        UpdateContractRequest request = new UpdateContractRequest(
                publicEntity.getId(),
                manager.getId(),
                "ct-001/2026",
                "Consultoria técnica atualizada",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("120000.00"),
                ContractStatus.ACTIVE
        );
        AuthenticatedUser authenticatedUser = authenticatedUser(tenantId);
        ContractResponse response = response();

        when(repository.findByIdAndTenant_Id(contractId, tenantId))
                .thenReturn(Optional.of(contract));
        when(repository.existsByTenant_IdAndNumberAndIdNot(
                tenantId,
                "CT-001/2026",
                contractId
        )).thenReturn(false);
        when(membershipRepository.findByIdAndTenant_IdAndStatus(
                manager.getId(),
                tenantId,
                TenantMembershipStatus.ACTIVE
        )).thenReturn(Optional.of(manager));
        when(repository.saveAndFlush(contract)).thenReturn(contract);
        when(mapper.toResponse(contract)).thenReturn(response);

        assertEquals(response, service.update(
                contractId,
                request,
                authenticatedUser
        ));

        verify(mapper).update(
                contract,
                request,
                publicEntity,
                manager,
                "CT-001/2026",
                authenticatedUser.id()
        );
    }


    @Test
    void shouldListContractsWithNormalizedFilters() {

        UUID tenantId = UUID.randomUUID();
        UUID publicEntityId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 12, 31);
        Contract contract = Contract.builder().build();
        ContractResponse response = response();
        ContractFilter filter = new ContractFilter(
                " ct-% ",
                publicEntityId,
                managerId,
                ContractStatus.ACTIVE,
                startDate,
                endDate
        );
        PageRequest pageable = PageRequest.of(
                0,
                20,
                Sort.by("startDate").descending()
                        .and(Sort.by("id").ascending())
        );

        when(repository.search(
                tenantId,
                "ct-\\%",
                publicEntityId,
                managerId,
                ContractStatus.ACTIVE,
                startDate,
                endDate,
                pageable
        )).thenReturn(new PageImpl<>(List.of(contract), pageable, 1));
        when(mapper.toResponse(contract)).thenReturn(response);

        ContractPageResponse result = service.findAll(
                filter,
                0,
                20,
                authenticatedUser(tenantId)
        );

        assertEquals(List.of(response), result.content());
        assertEquals(1, result.totalElements());
    }


    private CreateContractRequest createRequest(
            ContractStatus status,
            UUID managerId
    ) {

        return new CreateContractRequest(
                UUID.randomUUID(),
                managerId,
                "ct-001/2026",
                "Consultoria técnica especializada",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("100000.00"),
                status
        );
    }


    private UpdateContractRequest updateRequest(
            ContractStatus status,
            UUID managerId
    ) {

        return new UpdateContractRequest(
                activePublicEntity().getId(),
                managerId,
                "ct-001/2026",
                "Consultoria técnica atualizada",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("120000.00"),
                status
        );
    }


    private PublicEntity activePublicEntity() {

        return PublicEntity.builder()
                .id(UUID.randomUUID())
                .active(true)
                .build();
    }


    private ContractResponse response() {

        Instant now = Instant.now();

        return new ContractResponse(
                UUID.randomUUID(),
                null,
                null,
                "CT-001/2026",
                "Consultoria técnica especializada",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("100000.00"),
                ContractStatus.DRAFT,
                UUID.randomUUID(),
                UUID.randomUUID(),
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
                Set.of("CONTRACT_CREATE", "CONTRACT_READ", "CONTRACT_UPDATE")
        );
    }
}
