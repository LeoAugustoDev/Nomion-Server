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
import com.nomion.handler.UnauthorizedException;
import com.nomion.publicentity.domain.PublicEntity;
import com.nomion.publicentity.repository.PublicEntityRepository;
import com.nomion.security.AuthenticatedUser;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.repository.TenantMembershipRepository;
import com.nomion.tenant.repository.TenantRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class ContractService {


    private static final LocalDate MIN_FILTER_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate MAX_FILTER_DATE = LocalDate.of(9999, 12, 31);


    private final ContractRepository repository;
    private final TenantRepository tenantRepository;
    private final PublicEntityRepository publicEntityRepository;
    private final TenantMembershipRepository membershipRepository;
    private final ContractMapper mapper;


    public ContractService(
            ContractRepository repository,
            TenantRepository tenantRepository,
            PublicEntityRepository publicEntityRepository,
            TenantMembershipRepository membershipRepository,
            ContractMapper mapper
    ) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.publicEntityRepository = publicEntityRepository;
        this.membershipRepository = membershipRepository;
        this.mapper = mapper;
    }


    @Transactional
    public ContractResponse create(
            CreateContractRequest request,
            AuthenticatedUser authenticatedUser
    ) {

        UUID tenantId = requireTenantId(authenticatedUser);
        String number = normalizeNumber(request.number());

        validateInitialStatus(request.status());
        validateDates(request.startDate(), request.endDate());
        validateUniqueNumber(tenantId, number, null);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant não encontrado"));
        PublicEntity publicEntity = findActivePublicEntity(
                request.publicEntityId(),
                tenantId
        );
        TenantMembership manager = findActiveManager(
                request.managerMembershipId(),
                tenantId
        );

        validateActiveContract(request.status(), publicEntity, manager);

        Contract contract = mapper.toEntity(
                request,
                tenant,
                publicEntity,
                manager,
                number,
                authenticatedUser.id()
        );

        return save(contract);
    }


    @Transactional(readOnly = true)
    public ContractResponse findById(
            UUID id,
            AuthenticatedUser authenticatedUser
    ) {

        return mapper.toResponse(findInTenant(
                id,
                requireTenantId(authenticatedUser)
        ));
    }


    @Transactional(readOnly = true)
    public ContractPageResponse findAll(
            ContractFilter filter,
            int page,
            int size,
            AuthenticatedUser authenticatedUser
    ) {

        UUID tenantId = requireTenantId(authenticatedUser);

        validateFilterDates(filter.startDateFrom(), filter.endDateTo());

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("startDate").descending()
                        .and(Sort.by("id").ascending())
        );
        Page<ContractResponse> result = repository.search(
                        tenantId,
                        normalizeNumberFilter(filter.number()),
                        filter.publicEntityId(),
                        filter.managerMembershipId(),
                        filter.status(),
                        filter.startDateFrom() == null
                                ? MIN_FILTER_DATE
                                : filter.startDateFrom(),
                        filter.endDateTo() == null
                                ? MAX_FILTER_DATE
                                : filter.endDateTo(),
                        pageable
                )
                .map(mapper::toResponse);

        return ContractPageResponse.from(result);
    }


    @Transactional
    public ContractResponse update(
            UUID id,
            UpdateContractRequest request,
            AuthenticatedUser authenticatedUser
    ) {

        UUID tenantId = requireTenantId(authenticatedUser);
        Contract contract = findInTenant(id, tenantId);
        String number = normalizeNumber(request.number());

        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw new BusinessException("Contrato encerrado não pode ser alterado");
        }

        validateStatusTransition(contract.getStatus(), request.status());
        validateDates(request.startDate(), request.endDate());
        validateUniqueNumber(tenantId, number, id);

        PublicEntity publicEntity = resolvePublicEntity(
                contract,
                request.publicEntityId(),
                tenantId
        );
        TenantMembership manager = resolveManager(
                contract,
                request.managerMembershipId(),
                tenantId
        );

        validateActiveContract(request.status(), publicEntity, manager);
        mapper.update(
                contract,
                request,
                publicEntity,
                manager,
                number,
                authenticatedUser.id()
        );

        return save(contract);
    }


    private ContractResponse save(Contract contract) {

        try {
            return mapper.toResponse(repository.saveAndFlush(contract));
        } catch (DataIntegrityViolationException exception) {

            if (isConstraintViolation(
                    exception,
                    "uq_contracts_tenant_number"
            )) {
                throw new BusinessException(
                        "Número de contrato já utilizado no tenant"
                );
            }

            throw exception;
        }
    }


    private Contract findInTenant(UUID id, UUID tenantId) {

        return repository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Contrato não encontrado"));
    }


    private PublicEntity findActivePublicEntity(UUID id, UUID tenantId) {

        return publicEntityRepository.findByIdAndTenant_IdAndActiveTrue(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Órgão público ativo não encontrado"
                ));
    }


    private TenantMembership findActiveManager(UUID id, UUID tenantId) {

        if (id == null) {
            return null;
        }

        return membershipRepository.findByIdAndTenant_IdAndStatus(
                        id,
                        tenantId,
                        TenantMembershipStatus.ACTIVE
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Responsável ativo não encontrado"
                ));
    }


    private PublicEntity resolvePublicEntity(
            Contract contract,
            UUID requestedId,
            UUID tenantId
    ) {

        if (contract.getPublicEntity().getId().equals(requestedId)) {
            return contract.getPublicEntity();
        }

        return findActivePublicEntity(requestedId, tenantId);
    }


    private TenantMembership resolveManager(
            Contract contract,
            UUID requestedId,
            UUID tenantId
    ) {

        if (requestedId == null) {
            return null;
        }

        TenantMembership currentManager = contract.getManagerMembership();

        if (currentManager != null && currentManager.getId().equals(requestedId)) {
            return currentManager;
        }

        return findActiveManager(requestedId, tenantId);
    }


    private void validateInitialStatus(ContractStatus status) {

        if (status != ContractStatus.DRAFT && status != ContractStatus.ACTIVE) {
            throw new BusinessException(
                    "Contrato deve ser criado como rascunho ou ativo"
            );
        }
    }


    private void validateStatusTransition(
            ContractStatus current,
            ContractStatus target
    ) {

        if (!current.canTransitionTo(target)) {
            throw new BusinessException(
                    "Transição de situação do contrato não permitida"
            );
        }
    }


    private void validateDates(LocalDate startDate, LocalDate endDate) {

        if (startDate.isBefore(MIN_FILTER_DATE)
                || endDate.isAfter(MAX_FILTER_DATE)) {
            throw new BusinessException(
                    "Datas devem estar entre os anos 1 e 9999"
            );
        }

        if (startDate.isAfter(endDate)) {
            throw new BusinessException(
                    "Data inicial não pode ser posterior à data final"
            );
        }
    }


    private void validateFilterDates(
            LocalDate startDateFrom,
            LocalDate endDateTo
    ) {

        if (startDateFrom != null
                && endDateTo != null
                && startDateFrom.isAfter(endDateTo)) {
            throw new BusinessException("Período de filtro inválido");
        }
    }


    private void validateActiveContract(
            ContractStatus status,
            PublicEntity publicEntity,
            TenantMembership manager
    ) {

        if (status != ContractStatus.ACTIVE) {
            return;
        }

        if (!publicEntity.isActive()) {
            throw new BusinessException(
                    "Contrato ativo exige órgão público ativo"
            );
        }

        if (manager == null || manager.getStatus() != TenantMembershipStatus.ACTIVE) {
            throw new BusinessException(
                    "Contrato ativo exige responsável ativo"
            );
        }
    }


    private void validateUniqueNumber(
            UUID tenantId,
            String number,
            UUID ignoredId
    ) {

        boolean exists = ignoredId == null
                ? repository.existsByTenant_IdAndNumber(tenantId, number)
                : repository.existsByTenant_IdAndNumberAndIdNot(
                        tenantId,
                        number,
                        ignoredId
                );

        if (exists) {
            throw new BusinessException(
                    "Número de contrato já utilizado no tenant"
            );
        }
    }


    private UUID requireTenantId(AuthenticatedUser authenticatedUser) {

        if (authenticatedUser == null || authenticatedUser.tenantId() == null) {
            throw new UnauthorizedException("Autenticação necessária");
        }

        return authenticatedUser.tenantId();
    }


    private String normalizeNumber(String number) {
        return number.trim().toUpperCase(Locale.ROOT);
    }


    private String normalizeNumberFilter(String number) {

        if (number == null || number.isBlank()) {
            return "";
        }

        return escapeLikePattern(number.trim().toLowerCase(Locale.ROOT));
    }


    private String escapeLikePattern(String value) {

        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }


    private boolean isConstraintViolation(
            DataIntegrityViolationException exception,
            String constraintName
    ) {

        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null ? null : cause.getMessage();

        return message != null && message.contains(constraintName);
    }
}
