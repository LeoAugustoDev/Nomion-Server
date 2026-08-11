package com.nomion.contract.repository;

import com.nomion.contract.domain.Contract;
import com.nomion.contract.domain.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID> {


    @EntityGraph(attributePaths = {
            "publicEntity",
            "managerMembership",
            "managerMembership.user"
    })
    Optional<Contract> findByIdAndTenant_Id(UUID id, UUID tenantId);


    boolean existsByTenant_IdAndNumber(UUID tenantId, String number);


    boolean existsByTenant_IdAndNumberAndIdNot(
            UUID tenantId,
            String number,
            UUID id
    );


    boolean existsByTenant_IdAndPublicEntity_IdAndStatus(
            UUID tenantId,
            UUID publicEntityId,
            ContractStatus status
    );


    @EntityGraph(attributePaths = {
            "publicEntity",
            "managerMembership",
            "managerMembership.user"
    })
    @Query("""
            SELECT contract
            FROM Contract contract
            WHERE contract.tenant.id = :tenantId
              AND (
                  :number = ''
                  OR LOWER(contract.number) LIKE CONCAT(:number, '%') ESCAPE '\\'
              )
              AND (
                  :publicEntityId IS NULL
                  OR contract.publicEntity.id = :publicEntityId
              )
              AND (
                  :managerMembershipId IS NULL
                  OR contract.managerMembership.id = :managerMembershipId
              )
              AND (:status IS NULL OR contract.status = :status)
              AND contract.startDate >= :startDateFrom
              AND contract.endDate <= :endDateTo
            """)
    Page<Contract> search(
            @Param("tenantId") UUID tenantId,
            @Param("number") String number,
            @Param("publicEntityId") UUID publicEntityId,
            @Param("managerMembershipId") UUID managerMembershipId,
            @Param("status") ContractStatus status,
            @Param("startDateFrom") LocalDate startDateFrom,
            @Param("endDateTo") LocalDate endDateTo,
            Pageable pageable
    );
}
