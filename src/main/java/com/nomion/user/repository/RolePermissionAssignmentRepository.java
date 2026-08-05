package com.nomion.user.repository;

import com.nomion.user.domain.RolePermissionAssignment;
import com.nomion.user.domain.RolePermissionAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionAssignmentRepository
        extends JpaRepository<RolePermissionAssignment, RolePermissionAssignmentId> {
}
