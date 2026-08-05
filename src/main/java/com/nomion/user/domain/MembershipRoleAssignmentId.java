package com.nomion.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MembershipRoleAssignmentId implements Serializable {

    @Column(name = "membership_id")
    private UUID membershipId;

    @Column(name = "role_id")
    private UUID roleId;
}
