package com.nomion.contract.mapper;

import com.nomion.contract.domain.Contract;
import com.nomion.contract.dto.ContractManagerResponse;
import com.nomion.contract.dto.ContractPublicEntityResponse;
import com.nomion.contract.dto.ContractResponse;
import com.nomion.contract.dto.CreateContractRequest;
import com.nomion.contract.dto.UpdateContractRequest;
import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ContractMapper {


    public Contract toEntity(
            CreateContractRequest request,
            Tenant tenant,
            PublicEntity publicEntity,
            TenantMembership managerMembership,
            String normalizedNumber,
            UUID actorUserId
    ) {

        return Contract.builder()
                .tenant(tenant)
                .publicEntity(publicEntity)
                .managerMembership(managerMembership)
                .number(normalizedNumber)
                .object(request.object())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .amount(request.amount())
                .status(request.status())
                .createdByUserId(actorUserId)
                .updatedByUserId(actorUserId)
                .build();
    }


    public void update(
            Contract contract,
            UpdateContractRequest request,
            PublicEntity publicEntity,
            TenantMembership managerMembership,
            String normalizedNumber,
            UUID actorUserId
    ) {

        contract.update(
                publicEntity,
                managerMembership,
                normalizedNumber,
                request.object(),
                request.startDate(),
                request.endDate(),
                request.amount(),
                request.status(),
                actorUserId
        );
    }


    public ContractResponse toResponse(Contract contract) {

        return new ContractResponse(
                contract.getId(),
                toPublicEntityResponse(contract.getPublicEntity()),
                toManagerResponse(contract.getManagerMembership()),
                contract.getNumber(),
                contract.getObject(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getAmount(),
                contract.getStatus(),
                contract.getCreatedByUserId(),
                contract.getUpdatedByUserId(),
                contract.getCreatedAt(),
                contract.getUpdatedAt()
        );
    }


    private ContractPublicEntityResponse toPublicEntityResponse(PublicEntity publicEntity) {

        return new ContractPublicEntityResponse(
                publicEntity.getId(),
                publicEntity.getName(),
                publicEntity.getCnpj()
        );
    }


    private ContractManagerResponse toManagerResponse(
            TenantMembership managerMembership
    ) {

        if (managerMembership == null) {
            return null;
        }

        return new ContractManagerResponse(
                managerMembership.getId(),
                managerMembership.getUser().getId(),
                managerMembership.getUser().getName(),
                managerMembership.getUser().getEmail()
        );
    }
}
