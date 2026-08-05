package com.nomion.tenant.repository;

import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {


    @EntityGraph(attributePaths = "tenant")
    List<TenantMembership> findAllByUser_IdAndStatus(
            UUID userId,
            TenantMembershipStatus status
    );


    @EntityGraph(attributePaths = {"user", "tenant"})
    Optional<TenantMembership> findByUser_IdAndTenant_IdAndStatus(
            UUID userId,
            UUID tenantId,
            TenantMembershipStatus status
    );


    boolean existsByUser_IdAndTenant_Id(UUID userId, UUID tenantId);
}
