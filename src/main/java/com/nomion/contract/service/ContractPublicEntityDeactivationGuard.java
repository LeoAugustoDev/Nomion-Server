package com.nomion.contract.service;

import com.nomion.contract.domain.ContractStatus;
import com.nomion.contract.repository.ContractRepository;
import com.nomion.handler.BusinessException;
import com.nomion.publicentity.service.PublicEntityDeactivationGuard;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ContractPublicEntityDeactivationGuard
        implements PublicEntityDeactivationGuard {


    private final ContractRepository repository;


    public ContractPublicEntityDeactivationGuard(ContractRepository repository) {
        this.repository = repository;
    }


    @Override
    public void validate(UUID tenantId, UUID publicEntityId) {

        if (repository.existsByTenant_IdAndPublicEntity_IdAndStatus(
                tenantId,
                publicEntityId,
                ContractStatus.ACTIVE
        )) {
            throw new BusinessException(
                    "Órgão público possui contrato ativo"
            );
        }
    }
}
