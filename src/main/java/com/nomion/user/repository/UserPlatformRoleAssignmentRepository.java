package com.nomion.user.repository;

import com.nomion.user.domain.UserPlatformRoleAssignment;
import com.nomion.user.domain.UserPlatformRoleAssignmentId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserPlatformRoleAssignmentRepository
        extends JpaRepository<UserPlatformRoleAssignment, UserPlatformRoleAssignmentId> {


    @EntityGraph(attributePaths = "role")
    List<UserPlatformRoleAssignment> findAllByUser_Id(UUID userId);
}
