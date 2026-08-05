package com.nomion.user.repository;

import com.nomion.user.domain.MembershipRoleAssignment;
import com.nomion.user.domain.MembershipRoleAssignmentId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MembershipRoleAssignmentRepository
        extends JpaRepository<MembershipRoleAssignment, MembershipRoleAssignmentId> {


    @EntityGraph(attributePaths = "role")
    List<MembershipRoleAssignment> findAllByMembership_Id(UUID membershipId);
}
