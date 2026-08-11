package com.nomion.contract.mapper;

import com.nomion.contract.domain.Contract;
import com.nomion.contract.domain.ContractStatus;
import com.nomion.contract.dto.ContractResponse;
import com.nomion.contract.dto.CreateContractRequest;
import com.nomion.contract.dto.UpdateContractRequest;
import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.user.domain.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContractMapperTest {


    private final ContractMapper mapper = new ContractMapper();


    @Test
    void shouldMapContractAndAuditActor() {

        UUID actorId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().build();
        PublicEntity publicEntity = PublicEntity.builder().build();
        CreateContractRequest request = new CreateContractRequest(
                UUID.randomUUID(),
                null,
                "  ct-001/2026  ",
                "  Consultoria técnica especializada  ",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("100000.00"),
                ContractStatus.DRAFT
        );

        Contract contract = mapper.toEntity(
                request,
                tenant,
                publicEntity,
                null,
                "CT-001/2026",
                actorId
        );

        assertEquals(tenant, contract.getTenant());
        assertEquals(publicEntity, contract.getPublicEntity());
        assertNull(contract.getManagerMembership());
        assertEquals("CT-001/2026", contract.getNumber());
        assertEquals("Consultoria técnica especializada", contract.getObject());
        assertEquals(actorId, contract.getCreatedByUserId());
        assertEquals(actorId, contract.getUpdatedByUserId());
    }


    @Test
    void shouldMapManagerSummaryAndUpdateActor() {

        UUID actorId = UUID.randomUUID();
        User managerUser = User.builder()
                .id(UUID.randomUUID())
                .name("Consultor")
                .email("consultor@nomion.com")
                .build();
        TenantMembership manager = TenantMembership.builder()
                .id(UUID.randomUUID())
                .user(managerUser)
                .build();
        PublicEntity publicEntity = PublicEntity.builder()
                .id(UUID.randomUUID())
                .name("Prefeitura Municipal")
                .cnpj("11222333000181")
                .build();
        Contract contract = Contract.builder()
                .id(UUID.randomUUID())
                .publicEntity(publicEntity)
                .status(ContractStatus.DRAFT)
                .createdByUserId(UUID.randomUUID())
                .build();
        UpdateContractRequest request = new UpdateContractRequest(
                publicEntity.getId(),
                manager.getId(),
                "CT-002/2026",
                "Assessoria administrativa",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2027, 1, 31),
                null,
                ContractStatus.ACTIVE
        );

        mapper.update(
                contract,
                request,
                publicEntity,
                manager,
                "CT-002/2026",
                actorId
        );
        ContractResponse response = mapper.toResponse(contract);

        assertEquals(actorId, response.updatedByUserId());
        assertEquals(manager.getId(), response.manager().membershipId());
        assertEquals(managerUser.getId(), response.manager().userId());
        assertEquals("Consultor", response.manager().name());
    }
}
