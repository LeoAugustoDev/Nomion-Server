package com.nomion.user.repository;

import com.nomion.user.domain.Role;
import com.nomion.user.domain.RoleScope;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {


    @EntityGraph(attributePaths = "tenant")
    @Query("""
            SELECT role
            FROM Role role
            WHERE role.code IN :codes
              AND role.scope = :scope
              AND role.active = true
              AND (role.tenant IS NULL OR role.tenant.id = :tenantId)
            """)
    List<Role> findAssignableTenantRoles(
            @Param("codes") Set<String> codes,
            @Param("scope") RoleScope scope,
            @Param("tenantId") UUID tenantId
    );


    Optional<Role> findByCodeAndScopeAndTenantIsNullAndActiveTrue(
            String code,
            RoleScope scope
    );
}
