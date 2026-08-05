package com.nomion.user.repository;

import com.nomion.user.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {


    @Query("""
            SELECT DISTINCT assignment.permission
            FROM RolePermissionAssignment assignment
            WHERE assignment.role.id IN :roleIds
            """)
    List<Permission> findAllByRoleIds(@Param("roleIds") Set<UUID> roleIds);
}
