package com.nomion.user.dto;

import com.nomion.tenant.dto.TenantMembershipResponse;

import java.util.List;
import java.util.UUID;

public record UserResponse(

        UUID id,

        String name,

        String email,

        List<TenantMembershipResponse> memberships

) {
}
